"""
Document Processor with LLM Enrichment + Azure Document Intelligence
Processes documents using DI for coordinate-aware chunking, with
UnstructuredFileLoader as a fallback when DI is not configured.

Metadata now includes di_page_spans — a list of paragraph bounding boxes
per chunk — enabling precise coordinate-based highlighting in doc_search.py
without any index schema changes (stored inside the existing metadata field).
"""

import io
import os
import json
import re
import tempfile
import traceback
import requests
import base64
from typing import List, Dict, Optional, Set, Tuple
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import unquote
from azure.storage.blob import BlobClient

# LangChain imports
from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_openai import AzureOpenAIEmbeddings
from langchain_community.document_loaders import UnstructuredFileLoader

from indexer_config import config
from folder_security import FolderSecurityManager


# =============================================================================
# DOCUMENT INTELLIGENCE LOADER
# =============================================================================

class DocumentIntelligenceLoader:
    """
    Loads documents via Azure Document Intelligence (prebuilt-read model).

    Returns a list of Document objects where each object's metadata contains
    di_page_spans — the paragraph bounding polygons that were used to build
    that chunk's text content.  This lets doc_search.py draw precise
    annotations instead of doing a blind text search across the PDF.

    Falls back gracefully to UnstructuredFileLoader if DI is unavailable
    or if the file type doesn't benefit from DI (plain .txt files).
    """

    # Extensions where DI adds real value (OCR + layout for PDFs/DOCX,
    # native layout for image files).  Plain .txt needs no DI.
    DI_SUPPORTED = {'.pdf', '.docx', '.doc', '.png', '.jpg', '.jpeg', '.tiff', '.bmp'}

    def __init__(self):
        self.endpoint  = config.document_intelligence.endpoint
        self.api_key   = config.document_intelligence.api_key
        self.api_ver   = config.document_intelligence.api_version
        self.enabled   = config.document_intelligence.is_configured

    # ------------------------------------------------------------------
    # Public
    # ------------------------------------------------------------------

    def load(self, file_path: str, blob_uri: str) -> Optional[List[Document]]:
        """
        Load a document, returning LangChain Document objects.

        If DI is enabled and the file extension is supported, use DI and
        attach di_page_spans to each document's metadata.
        Otherwise fall back to UnstructuredFileLoader.

        Args:
            file_path : local path to the downloaded file
            blob_uri  : original blob URI (stored in metadata)

        Returns:
            List of Document objects, or None on complete failure.
        """
        ext = Path(file_path).suffix.lower()

        if self.enabled and ext in self.DI_SUPPORTED:
            try:
                return self._load_with_di(file_path, blob_uri)
            except Exception as e:
                print(f"   ⚠️  DI failed ({e}), falling back to Unstructured")

        return self._load_with_unstructured(file_path, blob_uri)

    # ------------------------------------------------------------------
    # DI path
    # ------------------------------------------------------------------

    def _load_with_di(self, file_path: str, blob_uri: str) -> List[Document]:
        """
        Call DI prebuilt-read, parse the response, and return one Document
        whose content is the full text and whose metadata has di_page_spans.

        di_page_spans is a list of dicts, one per DI paragraph:
            {
                "page":           int,          # 1-based
                "paragraph_text": str,          # raw text from DI
                "polygon":        List[float]   # [x1,y1, x2,y2, x3,y3, x4,y4] in points
            }

        The DocumentProcessor will split this document into chunks and
        assign the appropriate spans to each chunk.
        """
        print(f"   🧠 Analyzing with Document Intelligence...")

        # Read file bytes and base64-encode for the REST API
        with open(file_path, "rb") as f:
            file_bytes = f.read()
        b64_content = base64.b64encode(file_bytes).decode("utf-8")

        ext = Path(file_path).suffix.lower().lstrip(".")
        content_type_map = {
            "pdf":  "application/pdf",
            "docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "doc":  "application/msword",
            "png":  "image/png",
            "jpg":  "image/jpeg",
            "jpeg": "image/jpeg",
            "tiff": "image/tiff",
            "bmp":  "image/bmp",
        }
        content_type = content_type_map.get(ext, "application/octet-stream")

        # Submit analysis job
        submit_url = (
            f"{self.endpoint.rstrip('/')}/documentintelligence/documentModels/"
            f"prebuilt-read:analyze?api-version={self.api_ver}"
        )
        headers = {
            "Ocp-Apim-Subscription-Key": self.api_key,
            "Content-Type": "application/json",
        }
        payload = {
            "base64Source": b64_content,
        }

        resp = requests.post(submit_url, headers=headers, json=payload, timeout=60)
        resp.raise_for_status()

        # DI is async — poll the operation URL from the response header
        operation_url = resp.headers.get("Operation-Location") or resp.headers.get("operation-location")
        if not operation_url:
            raise RuntimeError("DI did not return an Operation-Location header")

        result = self._poll_operation(operation_url)
        return self._parse_di_result(result, blob_uri)

    def _poll_operation(self, operation_url: str, max_wait: int = 120) -> Dict:
        """Poll a DI async operation until it succeeds or times out."""
        import time
        headers = {"Ocp-Apim-Subscription-Key": self.api_key}
        elapsed = 0
        interval = 3

        while elapsed < max_wait:
            resp = requests.get(operation_url, headers=headers, timeout=30)
            resp.raise_for_status()
            data = resp.json()
            status = data.get("status", "")

            if status == "succeeded":
                return data.get("analyzeResult", {})
            elif status == "failed":
                raise RuntimeError(f"DI operation failed: {data.get('error', {})}")

            time.sleep(interval)
            elapsed += interval

        raise TimeoutError(f"DI operation did not complete within {max_wait}s")

    def _parse_di_result(self, result: Dict, blob_uri: str) -> List[Document]:
        """
        Parse DI analyzeResult into a single Document with di_page_spans.

        Each paragraph from DI becomes one entry in di_page_spans.
        The full document text is the concatenation of all paragraph texts
        in reading order, which is what gets chunked downstream.
        """
        paragraphs = result.get("paragraphs", [])

        # Fallback: if DI returned no paragraphs, use raw page content
        if not paragraphs:
            pages = result.get("pages", [])
            full_text = "\n".join(
                line.get("content", "")
                for page in pages
                for line in page.get("lines", [])
            )
            return [Document(
                page_content=full_text,
                metadata={
                    "url":          unquote(blob_uri),
                    "blob_uri":     unquote(blob_uri),
                    "source":       Path(blob_uri).name,
                    "page_number":  1,
                    "di_page_spans": [],
                    "di_processed": True,
                }
            )]

        di_page_spans = []
        text_parts    = []

        for para in paragraphs:
            para_text = para.get("content", "").strip()
            if not para_text:
                continue

            # bounding regions: list of {pageNumber, polygon}
            regions = para.get("boundingRegions", [])
            if regions:
                region   = regions[0]
                page_num = region.get("pageNumber", 1)
                polygon  = region.get("polygon", [])
            else:
                page_num = 1
                polygon  = []

            di_page_spans.append({
                "page":           page_num,
                "paragraph_text": para_text,
                "polygon":        polygon,
            })
            text_parts.append(para_text)

        full_text    = "\n\n".join(text_parts)
        first_page   = di_page_spans[0]["page"] if di_page_spans else 1

        return [Document(
            page_content=full_text,
            metadata={
                "url":           unquote(blob_uri),
                "blob_uri":      unquote(blob_uri),
                "source":        Path(blob_uri).name,
                "page_number":   first_page,
                "di_page_spans": di_page_spans,
                "di_processed":  True,
            }
        )]

    # ------------------------------------------------------------------
    # Unstructured fallback
    # ------------------------------------------------------------------

    def _load_with_unstructured(self, file_path: str, blob_uri: str) -> Optional[List[Document]]:
        """Load with UnstructuredFileLoader and attach standard metadata."""
        try:
            loader    = UnstructuredFileLoader(file_path)
            documents = loader.load()
            for doc in documents:
                doc.metadata["url"]          = unquote(blob_uri)
                doc.metadata["blob_uri"]     = unquote(blob_uri)
                doc.metadata["source"]       = Path(blob_uri).name
                doc.metadata["di_processed"] = False
                # No di_page_spans — highlight will fall back to text search
            return documents
        except Exception as e:
            print(f"   ❌ Unstructured failed: {e}")
            return None


# =============================================================================
# PARAGRAPH-AWARE CHUNKER
# =============================================================================

def assign_spans_to_chunks(
    chunks: List[Document],
    di_page_spans: List[Dict],
    chunk_size: int,
) -> None:
    """
    After splitting a DI document into chunks, assign the relevant
    di_page_spans entries to each chunk's metadata in-place.

    Strategy: for each chunk, find which DI paragraphs contributed to
    its text by checking whether the paragraph text appears (or overlaps)
    in the chunk content.  Store the matching spans so doc_search.py
    can use their polygons directly for highlighting.

    Args:
        chunks        : list of Document objects produced by the text splitter
        di_page_spans : full list of spans from the parent document
        chunk_size    : indexing chunk_size (used as a loose boundary hint)
    """
    if not di_page_spans:
        # Unstructured path — nothing to assign
        for chunk in chunks:
            chunk.metadata.setdefault("di_page_spans", [])
        return

    for chunk in chunks:
        chunk_text   = chunk.page_content
        matched      = []

        for span in di_page_spans:
            para_text = span["paragraph_text"]
            # A paragraph belongs to this chunk if any significant portion
            # of it appears in the chunk text (handles edge/overlap cases)
            if para_text in chunk_text:
                matched.append(span)
            elif len(para_text) > 20:
                # Partial overlap check: use the first 60 chars as a fingerprint
                fingerprint = para_text[:60].strip()
                if fingerprint in chunk_text:
                    matched.append(span)

        chunk.metadata["di_page_spans"] = matched

        # Use the first matched span's page as the authoritative page_number
        if matched:
            chunk.metadata["page_number"] = matched[0]["page"]


# =============================================================================
# DOCUMENT PROCESSOR
# =============================================================================

class DocumentProcessor:
    """Process documents with LLM enrichment, DI-backed loading, and embeddings."""

    def __init__(self):
        # Text splitter — still used to create fixed-size chunks from DI text
        self.text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=config.indexing.chunk_size,
            chunk_overlap=config.indexing.chunk_overlap
        )

        # Folder security
        self.security_manager = FolderSecurityManager()

        # DI loader (handles fallback internally)
        self.di_loader = DocumentIntelligenceLoader()

        # Embeddings
        self.embeddings = AzureOpenAIEmbeddings(
            azure_endpoint=config.azure_openai.endpoint,
            api_key=config.azure_openai.api_key,
            azure_deployment=config.azure_openai.embedding_deployment,
            model=config.azure_openai.embedding_deployment,
            openai_api_version=config.azure_openai.api_version,
            chunk_size=1
        )

        di_status = "✅ Enabled" if self.di_loader.enabled else "⚠️  Not configured (Unstructured fallback)"
        print(f"✅ Document Processor initialized")
        print(f"   Chunk size: {config.indexing.chunk_size}")
        print(f"   Chunk overlap: {config.indexing.chunk_overlap}")
        print(f"   Embeddings: ✅ Enabled")
        print(f"   LLM Enrichment: ✅ Enabled")
        print(f"   Document Intelligence: {di_status}")

    # ------------------------------------------------------------------
    # Public
    # ------------------------------------------------------------------

    def process_documents(self, blob_uris: Set[str]) -> tuple:
        """
        Process documents from blob URIs.

        Returns:
            tuple: (search_documents, indexing_results)
        """
        all_search_docs  = []
        indexing_results = []

        print(f"\n🔄 Processing {len(blob_uris)} documents...")

        for idx, blob_uri in enumerate(blob_uris, 1):
            filename = blob_uri.split('/')[-1]
            print(f"\n[{idx}/{len(blob_uris)}] Processing: {filename}")

            try:
                print(f"   📥 Loading from blob storage...")
                documents = self._load_document_from_blob(blob_uri)

                if not documents:
                    print(f"   ⚠️  No content loaded")
                    indexing_results.append({
                        'blob_uri':   blob_uri,
                        'file_name':  filename,
                        'success':    False,
                        'error':      'No content loaded',
                        'chunk_count': 0,
                        'doc_ids':    [],
                        'folder_id':  '0'
                    })
                    continue

                print(f"   ✓ Loaded {len(documents)} document(s)")

                # Folder security
                folder_id = self.security_manager.get_folder_id_from_blob_uri(blob_uri)

                # Split into chunks
                print(f"   ✂️  Splitting into chunks...")
                split_docs = self.text_splitter.split_documents(documents)
                print(f"   ✓ Created {len(split_docs)} chunks")

                # Assign DI spans to each chunk (no-op if DI was not used)
                di_page_spans = documents[0].metadata.get("di_page_spans", [])
                assign_spans_to_chunks(split_docs, di_page_spans, config.indexing.chunk_size)

                # Build search documents
                doc_ids = []
                for chunk_idx, doc in enumerate(split_docs):
                    enrichment     = self._enrich_with_llm(doc.page_content)
                    page_number    = self._get_safe_page_number(doc)
                    topics         = self._ensure_string_list(enrichment.get('topics', []))
                    example_queries = self._ensure_string_list(enrichment.get('example_queries', []))
                    intent_signals = self._ensure_string_list(enrichment.get('intent_signals', []))

                    doc_id = self._create_doc_id(blob_uri, chunk_idx)
                    doc_ids.append(doc_id)

                    # Build metadata JSON — includes di_page_spans when available
                    chunk_metadata = {
                        "url":           unquote(blob_uri),
                        "blob_uri":      unquote(blob_uri),
                        "source":        filename,
                        "page_number":   page_number,
                        "di_processed":  doc.metadata.get("di_processed", False),
                        "di_page_spans": doc.metadata.get("di_page_spans", []),
                    }

                    search_doc = {
                        'id':             doc_id,
                        'content':        str(doc.page_content),
                        'title':          str(self._extract_title(doc.page_content)),
                        'source':         str(filename),
                        'filepath':       str(blob_uri),
                        'file_type':      str(Path(filename).suffix.lower().replace('.', '')),
                        'chunk_id':       str(chunk_idx),
                        'page_number':    int(page_number),
                        'created_at':     datetime.now(timezone.utc).isoformat(),
                        'metadata':       json.dumps(chunk_metadata),
                        'topics':         topics,
                        'example_queries': example_queries,
                        'intent_signals': intent_signals,
                        'folder_id':      str(folder_id),
                        'blob_uri':       str(blob_uri),
                    }

                    # Embeddings
                    try:
                        embedding = self.embeddings.embed_query(doc.page_content)
                        search_doc['embedding'] = [float(x) for x in embedding]
                    except Exception as e:
                        print(f"   ⚠️  Embedding failed for chunk {chunk_idx}: {e}")

                    all_search_docs.append(search_doc)

                print(f"   ✅ Processed {len(split_docs)} chunks")

                indexing_results.append({
                    'blob_uri':    blob_uri,
                    'file_name':  filename,
                    'success':    True,
                    'error':      '',
                    'chunk_count': len(split_docs),
                    'doc_ids':    doc_ids,
                    'folder_id':  folder_id
                })

            except Exception as e:
                print(f"   ❌ Error processing {filename}: {e}")
                traceback.print_exc()
                indexing_results.append({
                    'blob_uri':    blob_uri,
                    'file_name':  filename,
                    'success':    False,
                    'error':      str(e),
                    'chunk_count': 0,
                    'doc_ids':    [],
                    'folder_id':  '0'
                })

        return all_search_docs, indexing_results

    # ------------------------------------------------------------------
    # Private: loading
    # ------------------------------------------------------------------

    def _load_document_from_blob(self, blob_uri: str) -> Optional[List[Document]]:
        """Download blob to a temp file, then load via DI or Unstructured."""
        try:
            container_name    = config.blob_storage.container_name
            connection_string = config.blob_storage.connection_string

            blob_name = blob_uri.split(f"/{container_name}/")[-1]
            blob_name = unquote(blob_name)

            client = BlobClient.from_connection_string(
                conn_str=connection_string,
                container_name=container_name,
                blob_name=blob_name
            )

            with tempfile.TemporaryDirectory() as temp_dir:
                safe_name = blob_name.replace('/', '_')
                file_path = os.path.join(temp_dir, safe_name)

                with open(file_path, "wb") as f:
                    client.download_blob().readinto(f)

                # DI loader handles both DI and Unstructured paths
                return self.di_loader.load(file_path, blob_uri)

        except Exception as e:
            print(f"   ❌ Error loading {blob_uri}: {e}")
            return None

    # ------------------------------------------------------------------
    # Private: LLM enrichment
    # ------------------------------------------------------------------

    def _enrich_with_llm(self, chunk_text: str) -> Dict:
        """Enrich document chunk with LLM-generated metadata."""
        prompt = f"""Analyze this document chunk and generate metadata to improve search and classification.

Based on the text, provide a JSON object with:
1. "topics": A list of 3-5 primary keywords
2. "example_queries": A list of 3 realistic user questions this chunk can answer
3. "intent_signals": A list of 1-3 terms from: "Policy Inquiry", "Procedural Guide", "Contact Information", "Definition/Explanation", "Data Request"

DOCUMENT CHUNK:
---
{chunk_text[:2000]}
---

Respond ONLY with valid JSON."""

        try:
            headers = {
                "api-key":       config.azure_openai.api_key,
                "Content-Type":  "application/json"
            }
            base_url   = config.azure_openai.endpoint.rstrip('/')
            deployment = config.azure_openai.enrichment_deployment
            version    = config.azure_openai.api_version.replace('--', '-')
            url        = f"{base_url}/openai/deployments/{deployment}/chat/completions?api-version={version}"

            payload = {
                "messages":        [{"role": "user", "content": prompt}],
                "response_format": {"type": "json_object"}
            }

            response = requests.post(url, headers=headers, json=payload, timeout=60)

            if response.status_code != 200:
                print(f"   ⚠️  LLM enrichment failed ({response.status_code}): {response.text}")
                return {'topics': [], 'example_queries': [], 'intent_signals': ['unknown']}

            llm_text = response.json()["choices"][0]["message"]["content"]
            return self._safe_parse_json(llm_text)

        except Exception as e:
            print(f"   ⚠️  LLM enrichment exception: {e}")
            return {'topics': [], 'example_queries': [], 'intent_signals': ['unknown']}

    # ------------------------------------------------------------------
    # Private: utilities
    # ------------------------------------------------------------------

    def _safe_parse_json(self, response_text: str) -> dict:
        try:
            return json.loads(response_text)
        except json.JSONDecodeError:
            match = re.search(r'\{.*\}', response_text, re.DOTALL)
            if match:
                try:
                    return json.loads(match.group(0))
                except json.JSONDecodeError:
                    pass
        return {"topics": [], "example_queries": [], "intent_signals": ["unknown"]}

    def _get_safe_page_number(self, doc: Document) -> int:
        # Prefer DI-assigned page_number in metadata (most accurate)
        for key in ('page_number', 'page'):
            page_val = doc.metadata.get(key)
            if page_val is None:
                continue
            if isinstance(page_val, (list, tuple)):
                page_val = page_val[0] if page_val else None
            try:
                return int(page_val)
            except (ValueError, TypeError):
                continue
        return 0

    def _ensure_string_list(self, value) -> List[str]:
        if not isinstance(value, list):
            value = [value] if value else []
        return [str(v).strip() for v in value if v]

    def _create_doc_id(self, source_identifier: str, chunk_idx: int) -> str:
        doc_id_str = f"{source_identifier}-Chunk-{chunk_idx}"
        return base64.urlsafe_b64encode(doc_id_str.encode()).decode().rstrip('=')

    def _extract_title(self, text: str, max_length: int = 100) -> str:
        lines = [line.strip() for line in text.split('\n') if line.strip()]
        for line in lines:
            if len(line) > 10:
                return line[:max_length]
        return text.strip()[:max_length]


if __name__ == "__main__":
    processor = DocumentProcessor()
    print("✅ Document processor initialized successfully")

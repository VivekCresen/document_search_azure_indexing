package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.dto.DocumentChunk;
import com.cresensolutions.document_search_azure_indexing.dto.ParsedDocument;
import java.util.List;

/**
 * Service interface for segmenting/chunking parsed documents.
 * Splitting documents into smaller logical, semantic chunks is essential for accurate vector embeddings.
 */
public interface DocumentChunkingService {

    /**
     * Splits a parsed document's text content into small semantic segments (chunks)
     * based on character boundaries, page structure, and layout spans.
     *
     * @param parsedDocument the parsed document data structure holding text and spans
     * @return a list of document chunks ready for embedding and indexing
     */
    List<DocumentChunk> chunk(ParsedDocument parsedDocument);
}

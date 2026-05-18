"""
Configuration Module for Document Indexer
Manages all environment variables and settings
"""

import os
from dataclasses import dataclass
from typing import Optional
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

@dataclass
class AzureSearchConfig:
    """Azure AI Search configuration"""
    endpoint: str
    admin_key: str
    index_name: str
    api_version: str = "2023-11-01"

    @classmethod
    def from_env(cls):
        return cls(
            endpoint=os.getenv("AZURE_SEARCH_ENDPOINT"),
            admin_key=os.getenv("AZURE_SEARCH_ADMIN_KEY"),
            index_name=os.getenv("AZURE_SEARCH_INDEX_NAME"),
            api_version=os.getenv("AZURE_SEARCH_API_VERSION", "2023-11-01")
        )

@dataclass
class AzureOpenAIConfig:
    """Azure OpenAI configuration"""
    endpoint: str
    api_key: str
    embedding_deployment: str
    enrichment_deployment: str
    api_version: str = "2024-12-01-preview"

    @classmethod
    def from_env(cls):
        return cls(
            endpoint=os.getenv("AZURE_OPENAI_ENDPOINT"),
            api_key=os.getenv("AZURE_OPENAI_KEY"),
            embedding_deployment=os.getenv("AZURE_OPENAI_EMBEDDING_DEPLOYMENT", "text-embedding-ada-002"),
            enrichment_deployment=os.getenv("AZURE_OPENAI_RAG_DEPLOYMENT", "gpt-5-mini"),
            api_version=os.getenv("AZURE_OPENAI_API_VERSION", "2023-05-15")
        )

@dataclass
class AzureDocumentIntelligenceConfig:
    """Azure Document Intelligence configuration"""
    endpoint: str
    api_key: str
    api_version: str = "2024-02-29-preview"

    @classmethod
    def from_env(cls):
        return cls(
            endpoint=os.getenv("AZURE_DI_ENDPOINT"),
            api_key=os.getenv("AZURE_DI_KEY"),
            api_version=os.getenv("AZURE_DI_API_VERSION", "2024-02-29-preview")
        )

    @property
    def is_configured(self) -> bool:
        """Returns True if both endpoint and key are set in the environment."""
        return bool(self.endpoint and self.api_key)

@dataclass
class BlobStorageConfig:
    """Azure Blob Storage configuration"""
    account_name: str
    account_key: str
    connection_string: str
    container_name: str
    directory: str = ""

    @classmethod
    def from_env(cls):
        account_name = os.getenv("AZURE_STORAGE_ACCOUNT_NAME")
        account_key  = os.getenv("AZURE_STORAGE_ACCOUNT_KEY")

        connection_string = os.getenv(
            "AZURE_STORAGE_CONNECTION_STRING",
            f"DefaultEndpointsProtocol=https;AccountName={account_name};"
            f"AccountKey={account_key};EndpointSuffix=core.windows.net"
        )

        return cls(
            account_name=account_name,
            account_key=account_key,
            connection_string=connection_string,
            container_name=os.getenv("INDEXING_CONTAINER_NAME", "destination-docs"),
            directory=os.getenv("INDEXING_DIRECTORY", "")
        )

@dataclass
class PostgreSQLConfig:
    """PostgreSQL configuration for folder security"""
    host: str
    database: str
    user: str
    password: str
    port: int = 5432

    @classmethod
    def from_env(cls):
        return cls(
            host=os.getenv("DB_HOST"),
            database=os.getenv("DB_NAME"),
            user=os.getenv("DB_USER"),
            password=os.getenv("DB_PASSWORD"),
            port=int(os.getenv("DB_PORT", "5432"))
        )

@dataclass
class IndexingConfig:
    """Document indexing configuration"""
    chunk_size: int = 1500
    chunk_overlap: int = 200
    supported_extensions: tuple = ('.pdf', '.docx', '.txt')
    max_retries: int = 3
    batch_size: int = 100

    # Always on features (as per requirements)
    use_embeddings: bool = True
    enable_llm_enrichment: bool = True

    @classmethod
    def from_env(cls):
        return cls(
            chunk_size=int(os.getenv("CHUNK_SIZE", "1500")),
            chunk_overlap=int(os.getenv("CHUNK_OVERLAP", "200")),
            max_retries=int(os.getenv("MAX_RETRIES", "3")),
            batch_size=int(os.getenv("BATCH_SIZE", "100"))
        )

class Config:
    """Master configuration class"""
    def __init__(self):
        self.azure_search              = AzureSearchConfig.from_env()
        self.azure_openai              = AzureOpenAIConfig.from_env()
        self.document_intelligence     = AzureDocumentIntelligenceConfig.from_env()
        self.blob_storage              = BlobStorageConfig.from_env()
        self.postgresql                = PostgreSQLConfig.from_env()
        self.indexing                  = IndexingConfig.from_env()

        # CSV tracking files
        self.files_in_blob_csv  = "files_in_blob.csv"
        self.indexed_files_csv  = "indexed_files.csv"
        self.indexing_log_csv   = "indexing_log.csv"

    def validate(self) -> bool:
        """Validate that all required configuration is present"""
        missing = []

        # Azure Search (required)
        if not self.azure_search.endpoint:
            missing.append("AZURE_SEARCH_ENDPOINT")
        if not self.azure_search.admin_key:
            missing.append("AZURE_SEARCH_ADMIN_KEY")
        if not self.azure_search.index_name:
            missing.append("AZURE_SEARCH_INDEX_NAME")

        # Azure OpenAI (required for embeddings and enrichment)
        if not self.azure_openai.endpoint:
            missing.append("AZURE_OPENAI_ENDPOINT")
        if not self.azure_openai.api_key:
            missing.append("AZURE_OPENAI_KEY")

        # Blob Storage (required)
        if not self.blob_storage.account_name:
            missing.append("AZURE_STORAGE_ACCOUNT_NAME")
        if not self.blob_storage.account_key:
            missing.append("AZURE_STORAGE_ACCOUNT_KEY")
        if not self.blob_storage.container_name:
            missing.append("INDEXING_CONTAINER_NAME")

        # PostgreSQL (required for folder security)
        if not self.postgresql.host:
            missing.append("DB_HOST")
        if not self.postgresql.database:
            missing.append("DB_NAME")
        if not self.postgresql.user:
            missing.append("DB_USER")
        if not self.postgresql.password:
            missing.append("DB_PASSWORD")

        # Document Intelligence (optional — warn but don't block)
        if not self.document_intelligence.is_configured:
            print("⚠️  AZURE_DI_ENDPOINT / AZURE_DI_KEY not set — "
                  "falling back to UnstructuredFileLoader (no coordinate highlighting)")

        if missing:
            print(f"❌ Missing required environment variables: {', '.join(missing)}")
            return False

        print("✅ All required configuration validated")
        return True

    def print_summary(self):
        """Print configuration summary"""
        print("\n" + "="*70)
        print("📋 INDEXER CONFIGURATION SUMMARY")
        print("="*70)

        print(f"\n🔍 Azure Search:")
        print(f"   Endpoint: {self.azure_search.endpoint}")
        print(f"   Index: {self.azure_search.index_name}")

        print(f"\n🤖 Azure OpenAI:")
        print(f"   Endpoint: {self.azure_openai.endpoint}")
        print(f"   Embedding Model: {self.azure_openai.embedding_deployment}")
        print(f"   Enrichment Model: {self.azure_openai.enrichment_deployment}")

        print(f"\n📄 Document Intelligence:")
        if self.document_intelligence.is_configured:
            print(f"   Endpoint: {self.document_intelligence.endpoint}")
            print(f"   Status: ✅ Enabled (coordinate-based highlighting)")
        else:
            print(f"   Status: ⚠️  Not configured (using Unstructured fallback)")

        print(f"\n📦 Blob Storage:")
        print(f"   Account: {self.blob_storage.account_name}")
        print(f"   Container: {self.blob_storage.container_name}")
        print(f"   Directory: {self.blob_storage.directory or '(root)'}")

        print(f"\n🗄️  PostgreSQL:")
        print(f"   Host: {self.postgresql.host}")
        print(f"   Database: {self.postgresql.database}")

        print(f"\n⚙️  Indexing Settings:")
        print(f"   Chunk Size: {self.indexing.chunk_size}")
        print(f"   Chunk Overlap: {self.indexing.chunk_overlap}")
        print(f"   Embeddings: ✅ Always Enabled")
        print(f"   LLM Enrichment: ✅ Always Enabled")
        print(f"   Batch Size: {self.indexing.batch_size}")

        print("="*70 + "\n")

# Global configuration instance
config = Config()

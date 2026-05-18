"""
Azure Search Index Manager
Creates and manages Azure AI Search indexes
"""

import traceback
import requests
from typing import List, Optional

from azure.core.credentials import AzureKeyCredential
from azure.search.documents import SearchClient
from azure.search.documents.indexes import SearchIndexClient
from azure.search.documents.indexes.models import (
    SearchIndex,
    SimpleField,
    SearchableField,
    SearchField,
    SearchFieldDataType,
    VectorSearch,
    HnswAlgorithmConfiguration,
    VectorSearchProfile,
    SemanticConfiguration,
    SemanticSearch,
    SemanticPrioritizedFields,
    SemanticField
)

from indexer_config import config

class IndexManager:
    """Manage Azure AI Search indexes"""
    
    def __init__(self):
        self.credential = AzureKeyCredential(config.azure_search.admin_key)
        self.endpoint = config.azure_search.endpoint
        self.index_client = SearchIndexClient(self.endpoint, self.credential)
        
        print(f"✅ Index Manager initialized")
        print(f"   Endpoint: {self.endpoint}")
    
    def create_index(self, index_name: str) -> bool:
        """Create Azure AI Search index with vector search"""
        print(f"\n🔨 Creating index: {index_name}")
        
        try:
            fields = [
                SimpleField(name="id", type=SearchFieldDataType.String, key=True),
                SearchableField(name="content", type=SearchFieldDataType.String, analyzer_name="en.microsoft"),
                SearchableField(name="title", type=SearchFieldDataType.String),
                SimpleField(name="source", type=SearchFieldDataType.String, filterable=True),
                SimpleField(name="filepath", type=SearchFieldDataType.String, filterable=True),
                SimpleField(name="file_type", type=SearchFieldDataType.String, filterable=True),
                SimpleField(name="chunk_id", type=SearchFieldDataType.String),
                SimpleField(name="page_number", type=SearchFieldDataType.Int32, filterable=True),
                SimpleField(name="created_at", type=SearchFieldDataType.DateTimeOffset, filterable=True),
                SearchableField(name="metadata", type=SearchFieldDataType.String),
                SearchField(name="topics", type=SearchFieldDataType.Collection(SearchFieldDataType.String), searchable=True, filterable=True),
                SearchField(name="example_queries", type=SearchFieldDataType.Collection(SearchFieldDataType.String), searchable=True),
                SearchField(name="intent_signals", type=SearchFieldDataType.Collection(SearchFieldDataType.String), searchable=True, filterable=True),
                SimpleField(name="folder_id", type=SearchFieldDataType.String, filterable=True, retrievable=True),
                SimpleField(name="blob_uri", type=SearchFieldDataType.String, filterable=True, retrievable=True),
                SearchField(name="embedding", type=SearchFieldDataType.Collection(SearchFieldDataType.Single), searchable=True, vector_search_dimensions=1536, vector_search_profile_name="my-vector-profile")
            ]
            
            vector_search = VectorSearch(
                algorithms=[HnswAlgorithmConfiguration(name="my-hnsw-config")],
                profiles=[VectorSearchProfile(name="my-vector-profile", algorithm_configuration_name="my-hnsw-config")]
            )
            
            semantic_search = SemanticSearch(
                configurations=[
                    SemanticConfiguration(
                        name="my-semantic-config",
                        prioritized_fields=SemanticPrioritizedFields(
                            title_field=SemanticField(field_name="title"),
                            content_fields=[SemanticField(field_name="content")],
                            keywords_fields=[SemanticField(field_name="topics"), SemanticField(field_name="intent_signals")]
                        )
                    )
                ]
            )
            
            index = SearchIndex(name=index_name, fields=fields, vector_search=vector_search, semantic_search=semantic_search)
            self.index_client.create_or_update_index(index)
            
            print(f"✅ Index '{index_name}' created successfully")
            return True
            
        except Exception as e:
            print(f"❌ Error creating index: {e}")
            traceback.print_exc()
            return False
    
    def list_indexes(self) -> List[str]:
        try:
            return [index.name for index in self.index_client.list_indexes()]
        except Exception as e:
            print(f"❌ Error listing indexes: {e}")
            return []
    
    def delete_index(self, index_name: str) -> bool:
        try:
            self.index_client.delete_index(index_name)
            print(f"✅ Index '{index_name}' deleted")
            return True
        except Exception as e:
            print(f"❌ Error deleting index: {e}")
            return False
    
    def get_index_stats(self, index_name: str) -> Optional[dict]:
        try:
            search_client = SearchClient(self.endpoint, index_name, self.credential)
            result = search_client.search("*", include_total_count=True, top=0)
            return {'name': index_name, 'document_count': result.get_count()}
        except Exception as e:
            print(f"❌ Error getting stats: {e}")
            return None
    
    def index_exists(self, index_name: str) -> bool:
        try:
            self.index_client.get_index(index_name)
            return True
        except:
            return False
    
    def upload_documents(self, index_name: str, documents: List[dict]) -> int:
        if not documents:
            return 0
        
        print(f"\n☁️  Uploading {len(documents)} documents...")
        
        try:
            search_client = SearchClient(self.endpoint, index_name, self.credential)
            batch_size = config.indexing.batch_size
            total_success = 0
            
            for i in range(0, len(documents), batch_size):
                batch = documents[i:i+batch_size]
                batch_num = (i // batch_size) + 1
                total_batches = (len(documents) + batch_size - 1) // batch_size
                
                print(f"   Batch {batch_num}/{total_batches} ({len(batch)} docs)...")
                
                result = search_client.upload_documents(documents=batch)
                success_count = sum(1 for r in result if r.succeeded)
                total_success += success_count
                
                if success_count < len(batch):
                    print(f"   ⚠️  {success_count}/{len(batch)} succeeded")
                else:
                    print(f"   ✓ All {success_count} uploaded")
            
            print(f"   ✅ Total: {total_success}/{len(documents)}")
            return total_success
            
        except Exception as e:
            print(f"   ❌ Error: {e}")
            traceback.print_exc()
            return 0
    
    def delete_documents_by_ids(self, index_name: str, doc_ids: List[str]) -> int:
        if not doc_ids:
            return 0
        
        print(f"\n🗑️  Deleting {len(doc_ids)} documents...")
        
        try:
            url = f"{self.endpoint}/indexes/{index_name}/docs/index?api-version={config.azure_search.api_version}"
            headers = {'Content-Type': 'application/json', 'api-key': config.azure_search.admin_key}
            
            deleted_count = 0
            batch_size = 100
            
            for i in range(0, len(doc_ids), batch_size):
                batch = doc_ids[i:i+batch_size]
                payload = {"value": [{"@search.action": "delete", "id": doc_id} for doc_id in batch]}
                response = requests.post(url, headers=headers, json=payload)
                
                if response.status_code == 200:
                    deleted_count += len(batch)
            
            print(f"   ✅ Deleted {deleted_count}")
            return deleted_count
            
        except Exception as e:
            print(f"   ❌ Error: {e}")
            return 0

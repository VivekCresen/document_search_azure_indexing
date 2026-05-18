"""
CSV Tracking Manager
Manages CSV files for tracking indexed documents
"""

import os
import csv
import pandas as pd
import requests
import json
from typing import Set, List, Dict, Tuple
from datetime import datetime
from urllib.parse import unquote
from azure.storage.blob import BlobServiceClient

from indexer_config import config

class CSVTrackingManager:
    """Manages CSV files for document tracking"""
    
    def __init__(self):
        self.files_in_blob_csv = config.files_in_blob_csv
        self.indexed_files_csv = config.indexed_files_csv
        self.indexing_log_csv = config.indexing_log_csv
        
        # Initialize log CSV if it doesn't exist
        self._initialize_log_csv()
        
        print(f"✅ CSV Tracking Manager initialized")
    
    def _initialize_log_csv(self):
        """Initialize indexing log CSV with headers"""
        if not os.path.exists(self.indexing_log_csv):
            with open(self.indexing_log_csv, 'w', newline='', encoding='utf-8') as f:
                writer = csv.writer(f)
                writer.writerow([
                    'blob_uri',
                    'folder_id', 
                    'file_name',
                    'indexed_at',
                    'chunk_count',
                    'index_status',
                    'search_doc_ids',
                    'error_message'
                ])
    
    def create_blob_inventory(self) -> int:
        """
        Create CSV inventory of files in blob storage
        
        Returns:
            int: Number of files found
        """
        print(f"\n📋 Creating blob inventory...")
        
        try:
            blob_service_client = BlobServiceClient.from_connection_string(
                config.blob_storage.connection_string
            )
            container_client = blob_service_client.get_container_client(
                config.blob_storage.container_name
            )
            
            blob_count = 0
            
            with open(self.files_in_blob_csv, 'w', newline='', encoding='utf-8') as f:
                writer = csv.writer(f)
                writer.writerow(["Blob Name", "Blob URI"])
                
                # List all blobs in the container
                blob_list = container_client.list_blobs(
                    name_starts_with=config.blob_storage.directory
                )
                
                for blob in blob_list:
                    blob_name_lower = blob.name.lower()
                    
                    # 1. Skip files with "highlighted" in the name
                    if "highlighted" in blob_name_lower:
                        continue

                    # 2. Skip excluded extensions
                    file_extension = os.path.splitext(blob_name_lower)[1]
                    if file_extension in ['.csv', '.xlsx', '.xls', '.xlm']:
                        continue
                    
                    # 3. Only include supported file types
                    if file_extension not in config.indexing.supported_extensions:
                        continue
                    
                    # Construct blob URI
                    blob_uri = f"https://{config.blob_storage.account_name}.blob.core.windows.net/{config.blob_storage.container_name}/{blob.name}"
                    decoded_uri = unquote(blob_uri)
                    
                    writer.writerow([blob.name, decoded_uri])
                    blob_count += 1
            
            print(f"   ✅ Found {blob_count} files in blob storage (excluding 'highlighted')")
            return blob_count
            
        except Exception as e:
            print(f"   ❌ Error creating blob inventory: {e}")
            raise
    
    def create_index_inventory(self) -> int:
        """
        Create CSV inventory of files already indexed
        
        Returns:
            int: Number of indexed files
        """
        print(f"\n📋 Creating index inventory...")
        
        try:
            url = f"{config.azure_search.endpoint}/indexes/{config.azure_search.index_name}/docs"
            params = {
                'api-version': config.azure_search.api_version,
                '$count': 'true',
                '$select': 'id,metadata'
            }
            headers = {
                "Content-Type": "application/json",
                "api-key": config.azure_search.admin_key
            }
            
            response = requests.get(url, headers=headers, params=params)
            
            if response.status_code != 200:
                print(f"   ⚠️  Index query failed (status {response.status_code})")
                # Create empty CSV
                pd.DataFrame(columns=['id', 'Blob URI']).to_csv(
                    self.indexed_files_csv, index=False
                )
                return 0
            
            data = response.json()
            documents = data.get('value', [])
            
            # Extract IDs and URLs
            ids = []
            urls = []
            
            for doc in documents:
                ids.append(doc.get('id', ''))
                
                # Parse metadata to extract URL
                metadata_str = doc.get('metadata', '{}')
                try:
                    metadata = json.loads(metadata_str)
                    url = metadata.get('url', '')
                    urls.append(unquote(url))
                except:
                    urls.append('')
            
            # Write to CSV
            df = pd.DataFrame({'id': ids, 'Blob URI': urls})
            df.to_csv(self.indexed_files_csv, index=False)
            
            print(f"   ✅ Found {len(documents)} indexed files")
            return len(documents)
            
        except Exception as e:
            print(f"   ❌ Error creating index inventory: {e}")
            # Create empty CSV
            pd.DataFrame(columns=['id', 'Blob URI']).to_csv(
                self.indexed_files_csv, index=False
            )
            return 0
    
    def compare_inventories(self) -> Dict:
        """
        Compare blob inventory with index inventory
        
        Returns:
            dict: Contains sets of URIs to index and delete
        """
        print(f"\n📊 Comparing inventories...")
        
        # Load both CSVs
        blob_files = self._load_csv_column_as_set(self.files_in_blob_csv, 'Blob URI')
        indexed_files = self._load_csv_column_as_set(self.indexed_files_csv, 'Blob URI')
        
        # Calculate differences
        to_index = blob_files - indexed_files
        to_delete = indexed_files - blob_files
        
        print(f"   📦 Blob files: {len(blob_files)}")
        print(f"   📚 Indexed files: {len(indexed_files)}")
        print(f"   ➕ Files to index: {len(to_index)}")
        print(f"   ➖ Files to delete: {len(to_delete)}")
        
        return {
            'to_index': to_index,
            'to_delete': to_delete,
            'blob_total': len(blob_files),
            'indexed_total': len(indexed_files)
        }
    
    def _load_csv_column_as_set(self, filename: str, column_name: str) -> Set[str]:
        """Load a specific column from CSV into a set"""
        try:
            if not os.path.exists(filename):
                return set()
            
            df = pd.read_csv(filename)
            
            if column_name not in df.columns:
                print(f"   ⚠️  Column '{column_name}' not found in {filename}")
                return set()
            
            return set(df[column_name].dropna())
            
        except Exception as e:
            print(f"   ❌ Error reading {filename}: {e}")
            return set()
    
    def get_doc_ids_for_uris(self, uris: Set[str]) -> Set[str]:
        """
        Get document IDs for given URIs from indexed_files.csv
        
        Args:
            uris: Set of blob URIs
            
        Returns:
            Set of document IDs
        """
        try:
            if not os.path.exists(self.indexed_files_csv):
                return set()
            
            df = pd.read_csv(self.indexed_files_csv)
            filtered_df = df[df['Blob URI'].isin(uris)]
            
            return set(filtered_df['id'].dropna())
            
        except Exception as e:
            print(f"   ❌ Error: {e}")
            return set()
    
    def log_indexing_result(
        self, 
        blob_uri: str,
        folder_id: str,
        file_name: str,
        chunk_count: int,
        doc_ids: List[str],
        success: bool = True,
        error_message: str = ""
    ):
        """
        Log indexing result to CSV
        
        Args:
            blob_uri: Full blob URI
            folder_id: PostgreSQL folder ID
            file_name: Name of the file
            chunk_count: Number of chunks created
            doc_ids: List of document IDs in search index
            success: Whether indexing succeeded
            error_message: Error message if failed
        """
        with open(self.indexing_log_csv, 'a', newline='', encoding='utf-8') as f:
            writer = csv.writer(f)
            writer.writerow([
                blob_uri,
                folder_id,
                file_name,
                datetime.now().isoformat(),
                chunk_count,
                "SUCCESS" if success else "FAILED",
                ",".join(doc_ids),
                error_message
            ])
    
    def get_indexed_files_summary(self) -> pd.DataFrame:
        """Get summary of indexed files from log"""
        if not os.path.exists(self.indexing_log_csv):
            return pd.DataFrame()
        
        return pd.read_csv(self.indexing_log_csv)
    
    def get_doc_ids_for_file(self, file_name: str) -> List[str]:
        """
        Get all document IDs for a specific file
        
        Args:
            file_name: Name of the file (with or without path)
            
        Returns:
            List of document IDs
        """
        try:
            if not os.path.exists(self.indexing_log_csv):
                return []
            
            df = pd.read_csv(self.indexing_log_csv)
            
            # Filter by file name (case-insensitive, partial match)
            mask = df['file_name'].str.contains(file_name, case=False, na=False)
            filtered_df = df[mask]
            
            if filtered_df.empty:
                return []
            
            # Get most recent entry for this file
            latest_entry = filtered_df.sort_values('indexed_at', ascending=False).iloc[0]
            
            # Parse doc IDs
            doc_ids_str = latest_entry['search_doc_ids']
            if pd.isna(doc_ids_str) or doc_ids_str == '':
                return []
            
            return [doc_id.strip() for doc_id in doc_ids_str.split(',')]
            
        except Exception as e:
            print(f"   ❌ Error: {e}")
            return []

if __name__ == "__main__":
    """Test the CSV tracking manager"""
    manager = CSVTrackingManager()
    
    # Test blob inventory
    print("\n1. Creating blob inventory...")
    blob_count = manager.create_blob_inventory()
    
    # Test index inventory
    print("\n2. Creating index inventory...")
    index_count = manager.create_index_inventory()
    
    # Test comparison
    print("\n3. Comparing inventories...")
    comparison = manager.compare_inventories()
    
    print(f"\n✅ Test complete!")
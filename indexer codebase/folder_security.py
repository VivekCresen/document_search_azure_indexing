"""
Folder Security Manager
Handles PostgreSQL folder_id lookup for document security
"""

import psycopg2
from typing import Dict
from urllib.parse import urlparse, unquote
from indexer_config import config

class FolderSecurityManager:
    """Manages folder-based security using PostgreSQL"""
    
    def __init__(self):
        self.db_config = {
            'host': config.postgresql.host,
            'database': config.postgresql.database,
            'user': config.postgresql.user,
            'password': config.postgresql.password,
            'port': config.postgresql.port
        }
        self._folder_cache: Dict[str, str] = {}
        
        print(f"✅ Folder Security Manager initialized")
        print(f"   Database: {config.postgresql.host}/{config.postgresql.database}")
    
    def get_folder_id_from_blob_uri(self, blob_uri: str) -> str:
        """
        Get folder_id from PostgreSQL based on blob URI
        Handles standard paths and user-specific paths (ending in /user_id/filename)
        
        Args:
            blob_uri: Full blob URI (e.g., https://storage/.../Cresen/nov-4/whitepapers/5/file.pdf)
            
        Returns:
            str: folder_id from documents table or "0" if not found
        """
        # Extract relative path from blob URI
        relative_path = self._extract_relative_path(blob_uri)
        
        # Check cache first
        if relative_path in self._folder_cache:
            return self._folder_cache[relative_path]
        
        # Lookup in database
        folder_id = self._lookup_folder_id(relative_path)
        
        # Cache the result
        self._folder_cache[relative_path] = folder_id
        
        return folder_id
    
    def _extract_relative_path(self, blob_uri: str) -> str:
        """Extract relative path from blob URI"""
        try:
            parsed_path = urlparse(blob_uri).path
            decoded_path = unquote(parsed_path)
            
            # Extract path after container name
            container_name = config.blob_storage.container_name
            
            if f"/{container_name}/" in decoded_path:
                relative_path = decoded_path.split(f"/{container_name}/")[-1]
            else:
                relative_path = decoded_path.lstrip('/')
            
            return relative_path
            
        except Exception as e:
            print(f"   ⚠️  Error extracting path from {blob_uri}: {e}")
            return ""
    
    def _lookup_folder_id(self, relative_path: str) -> str:
        """
        Lookup folder_id in PostgreSQL.
        Handles logic where the path might contain a user_id before the filename.
        
        Example: "Cresen/nov-4/whitepapers/5/MonitorMate Whitepaper.pdf"
        Target: file_name="MonitorMate Whitepaper.pdf", user_id=5, file_path="Cresen/nov-4/whitepapers/"
        """
        try:
            # Connect to database
            conn = psycopg2.connect(**self.db_config)
            cur = conn.cursor()
            
            # Split path into segments
            path_parts = relative_path.split('/')
            
            if not path_parts:
                return "0"
                
            file_name = path_parts[-1]
            
            # CHECK 1: Is this a user-specific file? (2nd to last part is a number)
            # Structure: .../parent_folder/user_id/filename
            if len(path_parts) >= 2 and path_parts[-2].isdigit():
                user_id = int(path_parts[-2])
                
                # The file path in DB is everything before the user_id
                # Remove filename and user_id from parts
                parent_path_parts = path_parts[:-2]
                
                # We need to clean up the prefix (e.g., remove "apps/mm/cresendemomm/")
                # Strategy: Try to match the suffix of the path in the DB
                
                # Construct a search pattern for file_path
                # We'll try to match exact file_name and user_id first
                query = """
                    SELECT id, file_path FROM prestage.documents 
                    WHERE file_name = %s 
                    AND user_id = %s
                    AND is_file = true
                """
                # FIX: Pass user_id as str(user_id) because DB column is character varying
                cur.execute(query, (file_name, str(user_id)))
                results = cur.fetchall()
                
                # If we found matches, verify the path
                if results:
                    # Reconstruct the raw path prefix from the blob structure
                    raw_prefix = "/".join(parent_path_parts) + "/" if parent_path_parts else ""
                    
                    for doc_id, db_file_path in results:
                        # Check if the DB path is part of our raw blob path
                        # e.g. raw="apps/mm/.../Cresen/nov-4/", db="Cresen/nov-4/"
                        if db_file_path and (db_file_path in raw_prefix or raw_prefix.endswith(db_file_path)):
                            print(f"   🔒 Matched File ID: {doc_id} (User: {user_id})")
                            cur.close()
                            conn.close()
                            return str(doc_id)
            
            # CHECK 2: Standard Folder Lookup (Fallback)
            # If not a user file, or user file lookup failed, try standard folder lookup
            # This logic mimics the original behavior for non-user files
            folder_segments = path_parts[:-1]
            
            while len(folder_segments) >= 1:
                target_folder_name = folder_segments[-1]
                parent_path_segments = folder_segments[:-1]
                
                # We need to handle the prefix issue here too. 
                # Simplest way: Check if the folder exists with roughly this name
                
                query = """
                    SELECT id FROM prestage.documents 
                    WHERE file_name = %s 
                    AND is_file = false
                    LIMIT 1
                """
                cur.execute(query, (target_folder_name,))
                result = cur.fetchone()
                
                if result:
                    folder_id = str(result[0])
                    # print(f"   🔒 Matched Folder: '{target_folder_name}' → id: {folder_id}") 
                    cur.close()
                    conn.close()
                    return folder_id
                
                folder_segments.pop()
            
            cur.close()
            conn.close()
            # print(f"   ⚠️  No match for: {relative_path}")
            return "0"
            
        except Exception as e:
            print(f"   ❌ Database error: {e}")
            return "0"
    
    def test_connection(self) -> bool:
        """Test PostgreSQL connection"""
        try:
            conn = psycopg2.connect(**self.db_config)
            cur = conn.cursor()
            cur.execute("SELECT 1")
            cur.close()
            conn.close()
            print("✅ PostgreSQL connection successful")
            return True
        except Exception as e:
            print(f"❌ PostgreSQL connection failed: {e}")
            return False
    
    def clear_cache(self):
        """Clear the folder lookup cache"""
        self._folder_cache.clear()
        print("✅ Folder cache cleared")

if __name__ == "__main__":
    """Test the security manager"""
    manager = FolderSecurityManager()
    if manager.test_connection():
        pass
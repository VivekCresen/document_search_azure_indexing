"""
Blob Listener & DB Configuration
Manages PostgreSQL schema, CSV migration, and real-time Blob status polling.
"""

import time
import os
import pandas as pd
import psycopg2
from urllib.parse import unquote
from azure.storage.blob import BlobServiceClient

from indexer_config import config
from folder_security import FolderSecurityManager

class BlobListener:
    def __init__(self):
        self.db_params = {
            'host': config.postgresql.host,
            'database': config.postgresql.database,
            'user': config.postgresql.user,
            'password': config.postgresql.password,
            'port': config.postgresql.port
        }
        self.blob_service = BlobServiceClient.from_connection_string(config.blob_storage.connection_string)
        self.container_name = config.blob_storage.container_name
        self.security = FolderSecurityManager()

    def get_conn(self):
        return psycopg2.connect(**self.db_params)

    def setup_database(self):
        """Creates the necessary tables if they don't exist."""
        print("🔨 Setting up database schema...")
        conn = self.get_conn()
        cur = conn.cursor()
        
        cur.execute("CREATE SCHEMA IF NOT EXISTS prestage;")
        
        cur.execute("""
            CREATE TABLE IF NOT EXISTS prestage.files_in_index (
                id SERIAL PRIMARY KEY,
                blob_uri TEXT UNIQUE NOT NULL,
                file_name TEXT NOT NULL,
                folder_id TEXT,
                status TEXT DEFAULT 'stable',
                chunk_ids TEXT, 
                chunk_count INT DEFAULT 0,
                last_modified_blob TIMESTAMP WITH TIME ZONE,
                last_indexed_at TIMESTAMP WITH TIME ZONE,
                error_message TEXT
            );
        """)
        
        cur.execute("""
            CREATE TABLE IF NOT EXISTS prestage.files_in_index_hist (
                hist_id SERIAL PRIMARY KEY,
                blob_uri TEXT,
                action TEXT,
                status_changed_to TEXT,
                changed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
            );
        """)
        
        conn.commit()
        cur.close()
        conn.close()
        print("✅ Database setup complete.")

    def migrate_from_csv(self):
        """Migrates existing data from CSV to PostgreSQL."""
        print("📋 Checking for CSV migration...")
        log_csv = config.indexing_log_csv
        
        if not os.path.exists(log_csv):
            print("   ⚠️ No CSV log found to migrate.")
            return

        conn = self.get_conn()
        cur = conn.cursor()
        
        # Check if DB is already populated
        cur.execute("SELECT COUNT(*) FROM prestage.files_in_index;")
        if cur.fetchone()[0] > 0:
            print("   ✅ Database already populated. Skipping CSV migration.")
            cur.close()
            conn.close()
            return

        print("   📥 Migrating existing CSV records to Database...")
        df = pd.read_csv(log_csv)
        
        # Keep only the latest entry per blob_uri
        df = df.sort_values('indexed_at', ascending=False).drop_duplicates('blob_uri')
        
        migrated_count = 0
        for _, row in df.iterrows():
            uri = str(row['blob_uri'])
            status = 'stable' if row['index_status'] == 'SUCCESS' else 'failed'
            
            cur.execute("""
                INSERT INTO prestage.files_in_index 
                (blob_uri, file_name, folder_id, status, chunk_ids, chunk_count, last_indexed_at, error_message)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (blob_uri) DO NOTHING
            """, (
                uri, str(row['file_name']), str(row['folder_id']), status, 
                str(row['search_doc_ids']), int(row['chunk_count']), 
                row['indexed_at'], str(row['error_message']) if pd.notna(row['error_message']) else ""
            ))
            migrated_count += 1
            
        conn.commit()
        cur.close()
        conn.close()
        print(f"   ✅ Migrated {migrated_count} records from CSV.")

    def poll_blob_storage(self):
        """Checks Blob Storage against the DB and marks changes."""
        print("\n🔍 Polling Azure Blob Storage for changes...")
        container_client = self.blob_service.get_container_client(self.container_name)
        
        # 1. Fetch current blob metadata
        blobs = container_client.list_blobs(name_starts_with=config.blob_storage.directory)
        azure_blobs = {}
        for b in blobs:
            if "highlighted" in b.name.lower():
                continue
            ext = os.path.splitext(b.name.lower())[1]
            if ext not in config.indexing.supported_extensions:
                continue

            uri = f"https://{config.blob_storage.account_name}.blob.core.windows.net/{self.container_name}/{b.name}"
            decoded_uri = unquote(uri)
            azure_blobs[decoded_uri] = {
                'name': b.name.split('/')[-1],
                'last_modified': b.last_modified
            }

        conn = self.get_conn()
        cur = conn.cursor()

        # 2. Identify Deletions (In DB but removed from Blob Storage)
        cur.execute("SELECT blob_uri FROM prestage.files_in_index WHERE status NOT IN ('to_be_deleted', 'deletion_inp')")
        db_uris = [row[0] for row in cur.fetchall()]
        
        for uri in db_uris:
            if uri not in azure_blobs:
                print(f"   🗑️  Marking for deletion: {uri.split('/')[-1]}")
                cur.execute("UPDATE prestage.files_in_index SET status = 'to_be_deleted' WHERE blob_uri = %s", (uri,))

        # 3. Identify Additions and Updates
        for uri, info in azure_blobs.items():
            cur.execute("SELECT last_modified_blob, status FROM prestage.files_in_index WHERE blob_uri = %s", (uri,))
            row = cur.fetchone()

            if not row:
                # Completely new file
                print(f"   ➕ Marking for ingestion (New): {info['name']}")
                folder_id = self.security.get_folder_id_from_blob_uri(uri)
                cur.execute("""
                    INSERT INTO prestage.files_in_index (blob_uri, file_name, folder_id, status, last_modified_blob)
                    VALUES (%s, %s, %s, 'to_be_ingested', %s)
                """, (uri, info['name'], folder_id, info['last_modified']))
                
            elif row[0] is None:
                # CSV MIGRATION FIX: 
                # We migrated this from CSV so we don't know the blob's original modified date.
                # Silently update the DB with Azure's current modified date to establish a baseline.
                print(f"   ⚓ Establishing baseline modified date for migrated file: {info['name']}")
                cur.execute("""
                    UPDATE prestage.files_in_index 
                    SET last_modified_blob = %s 
                    WHERE blob_uri = %s
                """, (info['last_modified'], uri))
                
            elif row[0] < info['last_modified']:
                # True update detected
                if row[1] not in ('ingestion_inp', 'deletion_inp'):
                    print(f"   🔄 Marking for ingestion (Updated): {info['name']}")
                    cur.execute("""
                        UPDATE prestage.files_in_index 
                        SET status = 'to_be_ingested', last_modified_blob = %s 
                        WHERE blob_uri = %s
                    """, (info['last_modified'], uri))

        conn.commit()
        cur.close()
        conn.close()

    def run(self, interval=60):
        self.setup_database()
        self.migrate_from_csv()
        print(f"📡 Listener active. Polling every {interval} seconds.")
        while True:
            try:
                self.poll_blob_storage()
            except Exception as e:
                print(f"❌ Error during polling cycle: {e}")
            time.sleep(interval)

if __name__ == "__main__":
    listener = BlobListener()
    listener.run()
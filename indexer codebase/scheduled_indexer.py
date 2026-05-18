"""
Scheduled Threaded Indexer
Picks up 'to_be_ingested' and 'to_be_deleted' files, manages state transitions, 
and processes files concurrently (max 4).
"""

import time
import psycopg2
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone

from indexer_config import config
from document_processor import DocumentProcessor
from search_index_manager import IndexManager

MAX_WORKERS = 4

class ScheduledIndexer:
    def __init__(self):
        self.db_params = {
            'host': config.postgresql.host,
            'database': config.postgresql.database,
            'user': config.postgresql.user,
            'password': config.postgresql.password,
            'port': config.postgresql.port
        }
        print(f"⚙️ Scheduled Indexer initialized (Max Threads: {MAX_WORKERS})")

    def get_conn(self):
        """Creates an isolated connection for each thread to prevent conflicts."""
        return psycopg2.connect(**self.db_params)

    def fetch_and_lock_jobs(self):
        """
        Retrieves pending tasks and immediately updates them to '_inp' status 
        so other parallel runs won't pick them up.
        """
        conn = self.get_conn()
        cur = conn.cursor()
        
        # Lock Ingestions
        cur.execute("""
            UPDATE prestage.files_in_index 
            SET status = 'ingestion_inp' 
            WHERE blob_uri IN (
                SELECT blob_uri FROM prestage.files_in_index 
                WHERE status = 'to_be_ingested' 
                LIMIT %s
            ) RETURNING blob_uri, 'ingest';
        """, (MAX_WORKERS * 2,)) # Fetch enough for a good batch
        ingest_jobs = cur.fetchall()

        # Lock Deletions
        cur.execute("""
            UPDATE prestage.files_in_index 
            SET status = 'deletion_inp' 
            WHERE blob_uri IN (
                SELECT blob_uri FROM prestage.files_in_index 
                WHERE status = 'to_be_deleted' 
                LIMIT %s
            ) RETURNING blob_uri, chunk_ids, 'delete';
        """, (MAX_WORKERS * 2,))
        delete_jobs = cur.fetchall()

        conn.commit()
        cur.close()
        conn.close()
        
        return ingest_jobs, delete_jobs

    def process_ingestion(self, uri: str):
        """Worker function for single file ingestion."""
        print(f"   [Thread] 🚀 Starting ingestion: {uri.split('/')[-1]}")
        processor = DocumentProcessor()
        index_mgr = IndexManager()
        
        try:
            search_docs, results = processor.process_documents({uri})
            
            doc_ids = []
            if search_docs:
                index_mgr.upload_documents(config.azure_search.index_name, search_docs)
                doc_ids = [doc['id'] for doc in search_docs]
            
            # Update DB mapping on success/fail
            res = results[0] if results else None
            success = res['success'] if res else False
            error_msg = res['error'] if res else "No content extracted"
            chunk_count = res['chunk_count'] if res else 0
            
            final_status = 'stable' if success else 'failed'
            chunk_ids_str = ",".join(doc_ids) if doc_ids else ""

            conn = self.get_conn()
            cur = conn.cursor()
            cur.execute("""
                UPDATE prestage.files_in_index 
                SET status = %s, chunk_ids = %s, chunk_count = %s, 
                    last_indexed_at = %s, error_message = %s
                WHERE blob_uri = %s
            """, (final_status, chunk_ids_str, chunk_count, datetime.now(timezone.utc), error_msg, uri))
            
            cur.execute("INSERT INTO prestage.files_in_index_hist (blob_uri, action, status_changed_to) VALUES (%s, %s, %s)",
                        (uri, 'INDEX_EXECUTION', final_status))
            conn.commit()
            cur.close()
            conn.close()
            
            return f"Ingested {uri.split('/')[-1]} ({final_status})"
            
        except Exception as e:
            # Fallback fail handler
            conn = self.get_conn()
            cur = conn.cursor()
            cur.execute("UPDATE prestage.files_in_index SET status = 'failed', error_message = %s WHERE blob_uri = %s", (str(e), uri))
            conn.commit()
            cur.close()
            conn.close()
            return f"Failed {uri.split('/')[-1]}: {e}"

    def process_deletion(self, uri: str, chunk_ids_str: str):
        """Worker function for single file deletion."""
        print(f"   [Thread] 🗑️ Starting deletion: {uri.split('/')[-1]}")
        index_mgr = IndexManager()
        
        try:
            if chunk_ids_str:
                doc_ids = [did.strip() for did in chunk_ids_str.split(',') if did.strip()]
                if doc_ids:
                    index_mgr.delete_documents_by_ids(config.azure_search.index_name, doc_ids)
            
            conn = self.get_conn()
            cur = conn.cursor()
            cur.execute("DELETE FROM prestage.files_in_index WHERE blob_uri = %s", (uri,))
            cur.execute("INSERT INTO prestage.files_in_index_hist (blob_uri, action, status_changed_to) VALUES (%s, %s, %s)",
                        (uri, 'DELETE_EXECUTION', 'deleted_from_db'))
            conn.commit()
            cur.close()
            conn.close()
            
            return f"Deleted {uri.split('/')[-1]}"
            
        except Exception as e:
            conn = self.get_conn()
            cur = conn.cursor()
            cur.execute("UPDATE prestage.files_in_index SET status = 'failed', error_message = %s WHERE blob_uri = %s", (str(e), uri))
            conn.commit()
            cur.close()
            conn.close()
            return f"Failed deletion {uri.split('/')[-1]}: {e}"

    def run_cycle(self):
        print("\n" + "="*50)
        print(f"🔄 Starting Scheduled Indexing Cycle")
        print("="*50)
        
        ingest_jobs, delete_jobs = self.fetch_and_lock_jobs()
        
        total_jobs = len(ingest_jobs) + len(delete_jobs)
        if total_jobs == 0:
            print("📭 No pending files to process.")
            return

        print(f"📦 Found {len(ingest_jobs)} ingestions and {len(delete_jobs)} deletions.")

        # Execute using ThreadPool
        with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
            futures = []
            
            # Queue deletions
            for job in delete_jobs:
                uri, chunk_ids, _ = job
                futures.append(executor.submit(self.process_deletion, uri, chunk_ids))
                
            # Queue ingestions
            for job in ingest_jobs:
                uri, _ = job
                futures.append(executor.submit(self.process_ingestion, uri))

            # Wait for results
            for future in as_completed(futures):
                try:
                    result = future.result()
                    print(f"   ✓ {result}")
                except Exception as exc:
                    print(f"   ❌ Thread generated an exception: {exc}")

        print("✅ Cycle Complete.")

if __name__ == "__main__":
    indexer = ScheduledIndexer()
    # For a persistent script, wrap this in a while loop with time.sleep()
    # Or call it via Cron / Azure Timer Trigger
    while True:
        indexer.run_cycle()
        time.sleep(120) # Runs every 2 minutes
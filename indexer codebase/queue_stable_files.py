import psycopg2
from indexer_config import config

def requeue_stable():
    print("🔄 Resetting 'stable' files back to 'to_be_ingested'...")
    conn = psycopg2.connect(
        host=config.postgresql.host,
        database=config.postgresql.database,
        user=config.postgresql.user,
        password=config.postgresql.password,
        port=config.postgresql.port
    )
    cur = conn.cursor()
    
    # Target files currently marked as stable
    cur.execute("""
        UPDATE prestage.files_in_index 
        SET status = 'to_be_ingested' 
        WHERE status = 'stable'
        RETURNING file_name;
    """)
    
    updated_files = cur.fetchall()
    conn.commit()
    cur.close()
    conn.close()
    
    print(f"✅ Queued {len(updated_files)} files for the stress test.")

if __name__ == "__main__":
    requeue_stable()
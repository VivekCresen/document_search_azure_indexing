# Document Indexer for Azure AI Search

Production-ready document indexing system with LangChain, LLM enrichment, and PostgreSQL security.

## 🌟 Features

- **✅ LLM Enrichment (Always On)**: GPT-4 generates topics, example queries, and intent signals
- **✅ Vector Embeddings (Always On)**: 1536-dim embeddings for semantic search
- **✅ Multi-Format Support**: PDF, DOCX, TXT
- **✅ PostgreSQL Security**: Folder-level access control
- **✅ CSV Tracking**: Track indexed files with document IDs for easy deletion
- **✅ Incremental Indexing**: Only process new files
- **✅ Interactive CLI**: Single command for all operations
- **✅ Error Recovery**: Continue on failure, log errors

## 📋 Prerequisites

- Python 3.8+
- Azure Storage Account
- Azure AI Search service
- Azure OpenAI service
- PostgreSQL database

## 🚀 Quick Start

### 1. Install Dependencies

```bash
pip install -r indexer_requirements.txt
```

### 2. Configure Environment

```bash
cp .env.indexer.template .env
# Edit .env with your Azure credentials
```

### 3. Set Up PostgreSQL

```sql
-- Your existing documents table structure
CREATE TABLE prestage.documents (
    id SERIAL PRIMARY KEY,
    file_name VARCHAR(255),
    file_path VARCHAR(1000),
    is_file BOOLEAN DEFAULT false,
    ...
);
```

### 4. Run the Indexer

```bash
python indexer.py
```

## 📁 File Structure

```
indexer/
├── indexer.py                  # Main CLI application (SINGLE ENTRY POINT)
├── indexer_config.py           # Configuration management
├── folder_security.py          # PostgreSQL folder security
├── csv_tracking.py             # CSV inventory management
├── document_processor.py       # LangChain document processing
├── search_index_manager.py     # Azure Search index management
├── indexer_requirements.txt    # Python dependencies
├── .env                        # Your configuration (create from template)
└── .env.indexer.template       # Configuration template
```

## 🎯 Usage

### Main Menu Options

```
📥 INDEXING OPERATIONS:
  1. Create/Update Index Schema
  2. Index New Files (Incremental)        ← Most common
  3. Re-index All Files (Full Refresh)
  4. Re-index Specific File

🗑️  DELETE OPERATIONS:
  5. Delete File from Index (by filename)
  6. Delete All Documents (clear index)

📊 INFORMATION:
  7. View Index Statistics
  8. View Indexed Files Log
  9. Validate CSV vs Index Sync

🔧 INDEX MANAGEMENT:
  10. List All Indexes
  11. Delete Index

🚪 EXIT:
  12. Exit
```

### Common Workflows

#### **First Time Setup**

```bash
python indexer.py

# Select option 1: Create Index
# Enter index name or accept default
# Confirm creation

# Select option 2: Index New Files
# Confirm to start indexing
```

#### **Daily Incremental Updates**

```bash
python indexer.py

# Select option 2: Index New Files
# Only new files since last run will be processed
```

#### **Delete a File**

```bash
python indexer.py

# Select option 5: Delete File from Index
# Enter filename (or part of it)
# Confirm deletion
```

## 🔒 Security Model

### How It Works

```
1. File Upload to Blob:
   "Cresen/nov-4/whitepapers/EngageMate.pdf"

2. PostgreSQL Lookup (hierarchical):
   Try: folder="whitepapers", path="Cresen/nov-4/"
   Try: folder="nov-4", path="Cresen/"
   Try: folder="Cresen", path=""
   → Returns: folder_id = "1235"

3. Document Tagging:
   Each chunk tagged with folder_id = "1235"

4. Query-Time Filtering (in chatbot):
   User permissions: allowed_folders = ["1234", "1235", "1236"]
   Search filter: folder_id IN ("1234", "1235", "1236")
   → User sees only authorized documents
```

### PostgreSQL Table Structure

```sql
-- Documents table (your existing structure)
prestage.documents (
    id SERIAL PRIMARY KEY,          -- This becomes folder_id
    file_name VARCHAR(255),         -- Folder or file name
    file_path VARCHAR(1000),        -- Parent path
    is_file BOOLEAN,                -- false for folders
    ...
)

-- Example data:
id=1231, file_name="Cresen", file_path="", is_file=false
id=1234, file_name="nov-4", file_path="Cresen/", is_file=false
id=1235, file_name="whitepapers", file_path="Cresen/nov-4/", is_file=false
```

## 📊 CSV Tracking Files

### `indexing_log.csv`

Tracks all indexing operations:

```csv
blob_uri,folder_id,file_name,indexed_at,chunk_count,index_status,search_doc_ids,error_message
"https://.../file.pdf","1235","file.pdf","2025-02-06T10:30:00",15,"SUCCESS","id1,id2,id3",""
```

**Use Cases:**
- Find document IDs for deletion
- Track which files were indexed
- Debug indexing failures

### `files_in_blob.csv` (temporary)

Lists files in blob storage (regenerated each run)

### `files_indexed.csv` (temporary)

Lists files in search index (regenerated each run)

## 🔧 Configuration

### Required Environment Variables

```env
# Azure Search
AZURE_SEARCH_ENDPOINT=https://...
AZURE_SEARCH_ADMIN_KEY=...
AZURE_SEARCH_INDEX_NAME=...

# Azure OpenAI (for embeddings + enrichment)
AZURE_OPENAI_ENDPOINT=https://...
AZURE_OPENAI_KEY=...

# Blob Storage
AZURE_STORAGE_ACCOUNT_NAME=...
AZURE_STORAGE_ACCOUNT_KEY=...
INDEXING_CONTAINER_NAME=destination-docs

# PostgreSQL (for folder security)
DB_HOST=...
DB_NAME=...
DB_USER=...
DB_PASS=...
```

### Optional Settings

```env
CHUNK_SIZE=1500              # Characters per chunk
CHUNK_OVERLAP=200            # Overlap between chunks
BATCH_SIZE=100               # Upload batch size
MAX_RETRIES=3                # Retry failed operations
```

## 📈 Index Schema

| Field | Type | Description |
|-------|------|-------------|
| **id** | String | Unique document ID |
| **content** | String | Document text |
| **title** | String | Extracted title |
| **source** | String | Filename |
| **filepath** | String | Full blob URI |
| **file_type** | String | File extension |
| **chunk_id** | String | Chunk number |
| **page_number** | Int32 | Page number |
| **created_at** | DateTimeOffset | Indexing timestamp |
| **metadata** | String | JSON metadata |
| **topics** | String[] | LLM-generated topics |
| **example_queries** | String[] | LLM-generated questions |
| **intent_signals** | String[] | LLM-generated categories |
| **folder_id** | String | PostgreSQL folder ID (security) |
| **blob_uri** | String | Full blob URI |
| **embedding** | Single[] | 1536-dim vector |

## 🐛 Troubleshooting

### "PostgreSQL connection failed"
```bash
python folder_security.py
# This will test the connection
```

### "No files to index"
- Check `INDEXING_CONTAINER_NAME` matches your blob container
- Verify files have supported extensions (.pdf, .docx, .txt)
- Run option 9 to validate sync

### "LLM enrichment timeout"
- Check Azure OpenAI quota/rate limits
- Each chunk gets enriched (can be slow for large files)

### "Embedding generation failed"
- Verify Azure OpenAI embeddings deployment exists
- Check API quota

## 📝 Example Usage Session

```
$ python indexer.py

✅ Configuration validated
✅ Folder Security Manager initialized
✅ CSV Tracking Manager initialized
✅ Document Processor initialized
✅ Index Manager initialized

📋 MAIN MENU
1. Create/Update Index Schema
2. Index New Files (Incremental)
...

Select option: 2

📥 INDEX NEW FILES (INCREMENTAL)

Index new files to 'my-index'? (yes/no): yes

📋 Step 1: Creating inventories...
   ✅ Found 25 files in blob storage
   ✅ Found 20 indexed files

📊 Step 2: Comparing...
   📦 Blob files: 25
   📚 Indexed files: 20
   ➕ Files to index: 5

📄 Step 3: Processing 5 files...

[1/5] Processing: report.pdf
   📥 Loading from blob storage...
   ✓ Loaded 1 document(s)
   🔒 Matched 'reports' → folder_id: 1240
   ✂️  Splitting into chunks...
   ✓ Created 12 chunks
   ✅ Processed 12 chunks

...

☁️  Uploading 58 documents...
   Batch 1/1 (58 docs)...
   ✓ All 58 uploaded
   ✅ Total: 58/58

✅ INDEXING COMPLETE
   Files processed: 5
   Chunks uploaded: 58
   Time elapsed: 3m 45s
```

## 🎛️ Advanced Features

### Delete Specific File

```python
# Option 5 in menu
# Enter filename: "report.pdf"
# System finds all chunks with matching name from CSV log
# Deletes all chunks from index
```

### Re-index Single File

```python
# Option 4 in menu
# Useful when file content changes
# Deletes old chunks, creates new ones
```

### Full Re-index

```python
# Option 3 in menu
# Clears entire index
# Re-processes all files from blob
# Use when schema changes or for cleanup
```

## 🔐 Security Integration with Chatbot

### In Your Chatbot Code

```python
from user_permissions import UserPermissionManager

# Get user's blocked folders
perm_manager = UserPermissionManager(db_config)
blocked_folders = perm_manager.get_blocked_folders(user_name, "VIEW")

# Build search filter
if blocked_folders:
    filter_str = f"not search.in(folder_id, '{','.join(blocked_folders)}', ',')"
else:
    filter_str = None

# Search with filter
results = search_client.search(
    search_text=query,
    filter=filter_str,
    top=5
)
```

## 📊 Performance

### Typical Processing Times

| Files | Avg Size | Chunks | Time |
|-------|----------|--------|------|
| 10 | 2MB | 150 | 2-3 min |
| 50 | 2MB | 750 | 10-15 min |
| 100 | 2MB | 1500 | 20-30 min |

**Factors:**
- LLM enrichment (each chunk = 1 API call)
- Embedding generation (each chunk = 1 API call)
- Network speed to Azure
- OpenAI rate limits

## 🤝 Support

For issues or questions:
1. Check configuration in `.env`
2. Run individual test files (e.g., `python folder_security.py`)
3. Review CSV logs for errors
4. Check Azure OpenAI quota/limits

## 📄 License

MIT License - Use and modify as needed

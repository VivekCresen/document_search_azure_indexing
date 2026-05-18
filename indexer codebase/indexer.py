"""
Document Indexer - Main CLI Application
Single entry point for all document indexing operations

Run: python indexer.py
"""

import sys
import time
from typing import Set

from indexer_config import config
from folder_security import FolderSecurityManager
from csv_tracking import CSVTrackingManager
from document_processor import DocumentProcessor
from search_index_manager import IndexManager

class DocumentIndexerCLI:
    """Interactive CLI for document indexing"""
    
    def __init__(self):
        print("\n" + "="*70)
        print("📚 DOCUMENT INDEXER FOR AZURE AI SEARCH")
        print("   LangChain + LLM Enrichment + Vector Embeddings")
        print("="*70)
        
        # Validate configuration
        if not config.validate():
            print("\n❌ Configuration validation failed!")
            print("Please check your .env file and try again.")
            sys.exit(1)
        
        # Print configuration summary
        config.print_summary()
        
        # Initialize components
        self.security_manager = FolderSecurityManager()
        self.csv_tracker = CSVTrackingManager()
        self.document_processor = DocumentProcessor()
        self.index_manager = IndexManager()
        
        self.current_index = config.azure_search.index_name
    
    def run(self):
        """Main CLI loop"""
        while True:
            self._print_menu()
            choice = input("\nSelect option (1-12): ").strip()
            
            if choice == '1':
                self._create_index()
            elif choice == '2':
                self._index_new_files()
            elif choice == '3':
                self._reindex_all_files()
            elif choice == '4':
                self._reindex_specific_file()
            elif choice == '5':
                self._delete_file_from_index()
            elif choice == '6':
                self._delete_all_documents()
            elif choice == '7':
                self._view_index_stats()
            elif choice == '8':
                self._view_indexed_files()
            elif choice == '9':
                self._validate_sync()
            elif choice == '10':
                self._list_indexes()
            elif choice == '11':
                self._delete_index()
            elif choice == '12':
                print("\n👋 Goodbye!")
                break
            else:
                print("❌ Invalid option. Please try again.")
    
    def _print_menu(self):
        """Print main menu"""
        print("\n" + "="*70)
        print("📋 MAIN MENU")
        print("="*70)
        
        print("\n📥 INDEXING OPERATIONS:")
        print("  1. Create/Update Index Schema")
        print("  2. Index New Files (Incremental)")
        print("  3. Re-index All Files (Full Refresh)")
        print("  4. Re-index Specific File")
        
        print("\n🗑️  DELETE OPERATIONS:")
        print("  5. Delete File from Index (by filename)")
        print("  6. Delete All Documents (clear index)")
        
        print("\n📊 INFORMATION:")
        print("  7. View Index Statistics")
        print("  8. View Indexed Files Log")
        print("  9. Validate CSV vs Index Sync")
        
        print("\n🔧 INDEX MANAGEMENT:")
        print("  10. List All Indexes")
        print("  11. Delete Index")
        
        print("\n🚪 EXIT:")
        print("  12. Exit")
        
        print(f"\n📌 Current Index: {self.current_index}")
    
    def _create_index(self):
        """Create or update search index"""
        print("\n" + "="*70)
        print("🔨 CREATE/UPDATE INDEX")
        print("="*70)
        
        index_name = input(f"\nIndex name (default: {self.current_index}): ").strip()
        if not index_name:
            index_name = self.current_index
        
        if self.index_manager.index_exists(index_name):
            print(f"\n⚠️  Index '{index_name}' already exists!")
            print("Creating it again will update schema but preserve existing data.")
            confirm = input("Continue? (yes/no): ")
            if confirm.lower() != 'yes':
                print("❌ Cancelled")
                return
        
        success = self.index_manager.create_index(index_name)
        
        if success:
            self.current_index = index_name
            config.azure_search.index_name = index_name
            print(f"\n✅ Index '{index_name}' ready!")
        
        input("\nPress Enter to continue...")
    
    def _index_new_files(self):
        """Index only new files (incremental)"""
        print("\n" + "="*70)
        print("📥 INDEX NEW FILES (INCREMENTAL)")
        print("="*70)
        
        if not self.index_manager.index_exists(self.current_index):
            print(f"\n❌ Index '{self.current_index}' does not exist!")
            print("Please create the index first (Option 1).")
            input("\nPress Enter to continue...")
            return
        
        confirm = input(f"\nIndex new files to '{self.current_index}'? (yes/no): ")
        if confirm.lower() != 'yes':
            print("❌ Cancelled")
            input("\nPress Enter to continue...")
            return
        
        start_time = time.time()
        
        # Step 1: Create inventories
        print("\n📋 Step 1: Creating inventories...")
        self.csv_tracker.create_blob_inventory()
        self.csv_tracker.create_index_inventory()
        
        # Step 2: Compare
        print("\n📊 Step 2: Comparing...")
        comparison = self.csv_tracker.compare_inventories()
        
        uris_to_index = comparison['to_index']
        
        if not uris_to_index:
            print("\n✅ No new files to index!")
            input("\nPress Enter to continue...")
            return
        
        # Step 3: Process and index
        print(f"\n📄 Step 3: Processing {len(uris_to_index)} files...")
        search_docs, indexing_results = self.document_processor.process_documents(uris_to_index)
        
        # Step 4: Upload to index
        if search_docs:
            uploaded = self.index_manager.upload_documents(self.current_index, search_docs)
            
            # Step 5: Log results
            for result in indexing_results:
                self.csv_tracker.log_indexing_result(
                    blob_uri=result['blob_uri'],
                    folder_id=result['folder_id'],
                    file_name=result['file_name'],
                    chunk_count=result['chunk_count'],
                    doc_ids=result['doc_ids'],
                    success=result['success'],
                    error_message=result['error']
                )
        
        elapsed = time.time() - start_time
        minutes = int(elapsed // 60)
        seconds = int(elapsed % 60)
        
        print(f"\n" + "="*70)
        print(f"✅ INDEXING COMPLETE")
        print(f"="*70)
        print(f"   Files processed: {len(indexing_results)}")
        print(f"   Chunks uploaded: {len(search_docs)}")
        print(f"   Time elapsed: {minutes}m {seconds}s")
        print("="*70)
        
        input("\nPress Enter to continue...")
    
    def _reindex_all_files(self):
        """Re-index all files (full refresh)"""
        print("\n" + "="*70)
        print("🔄 RE-INDEX ALL FILES (FULL REFRESH)")
        print("="*70)
        
        print("\n⚠️  WARNING: This will:")
        print("  1. Delete ALL documents from the index")
        print("  2. Re-index ALL files from blob storage")
        print("  3. May take significant time")
        
        confirm = input("\nContinue? (yes/no): ")
        if confirm.lower() != 'yes':
            print("❌ Cancelled")
            input("\nPress Enter to continue...")
            return
        
        start_time = time.time()
        
        # Step 1: Get all files from blob
        print("\n📋 Step 1: Getting all files from blob...")
        self.csv_tracker.create_blob_inventory()
        blob_files = self.csv_tracker._load_csv_column_as_set(
            self.csv_tracker.files_in_blob_csv, 'Blob URI'
        )
        
        # Step 2: Delete all docs from index
        print(f"\n🗑️  Step 2: Clearing index...")
        self.csv_tracker.create_index_inventory()
        indexed_files = self.csv_tracker._load_csv_column_as_set(
            self.csv_tracker.indexed_files_csv, 'Blob URI'
        )
        
        if indexed_files:
            doc_ids = self.csv_tracker.get_doc_ids_for_uris(indexed_files)
            self.index_manager.delete_documents_by_ids(self.current_index, list(doc_ids))
        
        # Step 3: Index all files
        print(f"\n📄 Step 3: Processing {len(blob_files)} files...")
        search_docs, indexing_results = self.document_processor.process_documents(blob_files)
        
        # Step 4: Upload
        if search_docs:
            self.index_manager.upload_documents(self.current_index, search_docs)
            
            # Log results
            for result in indexing_results:
                self.csv_tracker.log_indexing_result(
                    blob_uri=result['blob_uri'],
                    folder_id=result['folder_id'],
                    file_name=result['file_name'],
                    chunk_count=result['chunk_count'],
                    doc_ids=result['doc_ids'],
                    success=result['success'],
                    error_message=result['error']
                )
        
        elapsed = time.time() - start_time
        minutes = int(elapsed // 60)
        seconds = int(elapsed % 60)
        
        print(f"\n" + "="*70)
        print(f"✅ RE-INDEXING COMPLETE")
        print(f"="*70)
        print(f"   Files processed: {len(indexing_results)}")
        print(f"   Chunks uploaded: {len(search_docs)}")
        print(f"   Time elapsed: {minutes}m {seconds}s")
        print("="*70)
        
        input("\nPress Enter to continue...")
    
    def _reindex_specific_file(self):
        """Re-index a specific file"""
        print("\n" + "="*70)
        print("🔄 RE-INDEX SPECIFIC FILE")
        print("="*70)
        
        filename = input("\nEnter filename (or part of filename): ").strip()
        if not filename:
            print("❌ Filename cannot be empty")
            input("\nPress Enter to continue...")
            return
        
        # Find matching files in blob
        self.csv_tracker.create_blob_inventory()
        blob_files = self.csv_tracker._load_csv_column_as_set(
            self.csv_tracker.files_in_blob_csv, 'Blob URI'
        )
        
        matching = [uri for uri in blob_files if filename.lower() in uri.lower()]
        
        if not matching:
            print(f"\n❌ No files found matching '{filename}'")
            input("\nPress Enter to continue...")
            return
        
        if len(matching) > 1:
            print(f"\n⚠️  Multiple files found:")
            for i, uri in enumerate(matching, 1):
                print(f"   {i}. {uri.split('/')[-1]}")
            
            try:
                choice = int(input("\nSelect file number: "))
                if 1 <= choice <= len(matching):
                    selected_uri = matching[choice - 1]
                else:
                    print("❌ Invalid choice")
                    input("\nPress Enter to continue...")
                    return
            except ValueError:
                print("❌ Invalid input")
                input("\nPress Enter to continue...")
                return
        else:
            selected_uri = matching[0]
        
        print(f"\n📄 Re-indexing: {selected_uri.split('/')[-1]}")
        
        # Delete old chunks
        old_doc_ids = self.csv_tracker.get_doc_ids_for_file(filename)
        if old_doc_ids:
            print(f"   🗑️  Deleting {len(old_doc_ids)} old chunks...")
            self.index_manager.delete_documents_by_ids(self.current_index, old_doc_ids)
        
        # Process and index
        search_docs, indexing_results = self.document_processor.process_documents({selected_uri})
        
        if search_docs:
            self.index_manager.upload_documents(self.current_index, search_docs)
            
            for result in indexing_results:
                self.csv_tracker.log_indexing_result(
                    blob_uri=result['blob_uri'],
                    folder_id=result['folder_id'],
                    file_name=result['file_name'],
                    chunk_count=result['chunk_count'],
                    doc_ids=result['doc_ids'],
                    success=result['success'],
                    error_message=result['error']
                )
            
            print(f"\n✅ Re-indexed successfully!")
        
        input("\nPress Enter to continue...")
    
    def _delete_file_from_index(self):
        """Delete file chunks from index by filename"""
        print("\n" + "="*70)
        print("🗑️  DELETE FILE FROM INDEX")
        print("="*70)
        
        filename = input("\nEnter filename (or part of filename): ").strip()
        if not filename:
            print("❌ Filename cannot be empty")
            input("\nPress Enter to continue...")
            return
        
        # Get doc IDs for this file
        doc_ids = self.csv_tracker.get_doc_ids_for_file(filename)
        
        if not doc_ids:
            print(f"\n❌ No indexed chunks found for '{filename}'")
            input("\nPress Enter to continue...")
            return
        
        print(f"\n⚠️  Found {len(doc_ids)} chunks for '{filename}'")
        confirm = input("Delete all chunks? (yes/no): ")
        
        if confirm.lower() != 'yes':
            print("❌ Cancelled")
            input("\nPress Enter to continue...")
            return
        
        deleted = self.index_manager.delete_documents_by_ids(self.current_index, doc_ids)
        print(f"\n✅ Deleted {deleted} chunks")
        
        input("\nPress Enter to continue...")
    
    def _delete_all_documents(self):
        """Delete all documents from index"""
        print("\n" + "="*70)
        print("🗑️  DELETE ALL DOCUMENTS")
        print("="*70)
        
        print("\n⚠️  WARNING: This will delete ALL documents from the index!")
        confirm = input("Type the index name to confirm: ")
        
        if confirm != self.current_index:
            print("❌ Index name did not match. Cancelled.")
            input("\nPress Enter to continue...")
            return
        
        # Get all doc IDs
        self.csv_tracker.create_index_inventory()
        indexed_files = self.csv_tracker._load_csv_column_as_set(
            self.csv_tracker.indexed_files_csv, 'Blob URI'
        )
        
        if not indexed_files:
            print("\n✅ Index is already empty")
            input("\nPress Enter to continue...")
            return
        
        doc_ids = self.csv_tracker.get_doc_ids_for_uris(indexed_files)
        deleted = self.index_manager.delete_documents_by_ids(self.current_index, list(doc_ids))
        
        print(f"\n✅ Deleted {deleted} documents")
        input("\nPress Enter to continue...")
    
    def _view_index_stats(self):
        """View index statistics"""
        print("\n" + "="*70)
        print(f"📊 INDEX STATISTICS: {self.current_index}")
        print("="*70)
        
        stats = self.index_manager.get_index_stats(self.current_index)
        
        if stats:
            print(f"\n   Index Name: {stats['name']}")
            print(f"   Total Documents: {stats['document_count']}")
        else:
            print(f"\n❌ Could not retrieve stats")
        
        input("\nPress Enter to continue...")
    
    def _view_indexed_files(self):
        """View indexed files log"""
        print("\n" + "="*70)
        print("📝 INDEXED FILES LOG")
        print("="*70)
        
        summary = self.csv_tracker.get_indexed_files_summary()
        
        if summary.empty:
            print("\n📭 No indexed files in log")
        else:
            print(f"\nTotal entries: {len(summary)}\n")
            print(summary.tail(10).to_string(index=False))
        
        input("\nPress Enter to continue...")
    
    def _validate_sync(self):
        """Validate CSV vs index synchronization"""
        print("\n" + "="*70)
        print("🔍 VALIDATE CSV vs INDEX SYNC")
        print("="*70)
        
        self.csv_tracker.create_blob_inventory()
        self.csv_tracker.create_index_inventory()
        comparison = self.csv_tracker.compare_inventories()
        
        print(f"\n   ✅ In sync" if not (comparison['to_index'] or comparison['to_delete']) else "\n   ⚠️  Out of sync")
        
        input("\nPress Enter to continue...")
    
    def _list_indexes(self):
        """List all indexes"""
        print("\n" + "="*70)
        print("📚 AVAILABLE INDEXES")
        print("="*70)
        
        indexes = self.index_manager.list_indexes()
        
        if not indexes:
            print("\n📭 No indexes found")
        else:
            print(f"\nFound {len(indexes)} index(es):\n")
            for idx, name in enumerate(indexes, 1):
                is_current = " 👉 (current)" if name == self.current_index else ""
                print(f"  {idx}. {name}{is_current}")
                
                stats = self.index_manager.get_index_stats(name)
                if stats:
                    print(f"      Documents: {stats['document_count']}")
        
        input("\nPress Enter to continue...")
    
    def _delete_index(self):
        """Delete an index"""
        print("\n" + "="*70)
        print("🗑️  DELETE INDEX")
        print("="*70)
        
        indexes = self.index_manager.list_indexes()
        if not indexes:
            print("\n📭 No indexes available")
            input("\nPress Enter to continue...")
            return
        
        print("\nAvailable indexes:\n")
        for idx, name in enumerate(indexes, 1):
            print(f"  {idx}. {name}")
        
        try:
            choice = int(input("\nSelect index number (0 to cancel): "))
            
            if choice == 0:
                print("❌ Cancelled")
                return
            
            if 1 <= choice <= len(indexes):
                index_name = indexes[choice - 1]
                
                print(f"\n⚠️  WARNING: Delete '{index_name}'?")
                confirm = input("Type index name to confirm: ")
                
                if confirm == index_name:
                    self.index_manager.delete_index(index_name)
                else:
                    print("❌ Cancelled")
            else:
                print("❌ Invalid selection")
        except ValueError:
            print("❌ Invalid input")
        
        input("\nPress Enter to continue...")

def main():
    """Main entry point"""
    try:
        cli = DocumentIndexerCLI()
        cli.run()
    except KeyboardInterrupt:
        print("\n\n👋 Interrupted. Goodbye!")
        sys.exit(0)
    except Exception as e:
        print(f"\n❌ FATAL ERROR: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()

package com.cresensolutions.document_search_azure_indexing.repository;

import com.cresensolutions.document_search_azure_indexing.domain.IndexedChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository interface for managing persistent {@link IndexedChunk} entities.
 * Tracks individual document segments uploaded to the Azure vector search index.
 */
public interface IndexedChunkRepository extends JpaRepository<IndexedChunk, Long> {

    /**
     * Finds all indexed chunks corresponding to a specific blob storage URI.
     *
     * @param blobUri the target document's blob storage URI
     * @return a list of indexed chunk records
     */
    List<IndexedChunk> findByBlobUri(String blobUri);

    /**
     * Deletes all indexed chunk tracking records matching a specific blob URI.
     * Usually executed when removing or re-indexing a document.
     *
     * @param blobUri the target document's blob storage URI
     */
    void deleteByBlobUri(String blobUri);
}

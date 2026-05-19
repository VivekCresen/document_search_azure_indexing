package com.cresensolutions.document_search_azure_indexing.repository;

import com.cresensolutions.document_search_azure_indexing.domain.FileInIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing persistent {@link FileInIndex} entities.
 * Provides custom queries for finding files by blob URI, status, and active states.
 */
public interface FileInIndexRepository extends JpaRepository<FileInIndex, Long> {

    /**
     * Finds a file record by its fully-qualified Azure storage blob URI.
     *
     * @param blobUri the unique blob resource URI
     * @return an Optional containing the file record if found, or empty
     */
    Optional<FileInIndex> findByBlobUri(String blobUri);

    /**
     * Finds all file records currently matching a specific indexing/job status.
     *
     * @param status the job status value
     * @return a list of matching file records
     */
    List<FileInIndex> findByStatus(String status);

    /**
     * Retrieves the fully qualified storage URIs for all active indexed documents
     * that are not marked for or in the process of deletion.
     *
     * @return a list of active blob URI strings
     */
    @Query("select f.blobUri from FileInIndex f where f.status not in ('to_be_deleted', 'delete_inp', 'deleted')")
    List<String> findActiveBlobUris();
}

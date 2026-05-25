package com.cresensolutions.document_search_azure_indexing.repository;

import com.cresensolutions.document_search_azure_indexing.domain.IngestionJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing persistent {@link IngestionJob} instances.
 * Handles job scheduling, execution tracking, lock acquisition, and job metrics.
 */
public interface IngestionJobRepository extends JpaRepository<IngestionJob, Long> {

    /**
     * Finds a scheduled ingestion job by its target blob URI.
     *
     * @param blobUri the target document's blob storage URI
     * @return an Optional containing the job if found, or empty
     */
    Optional<IngestionJob> findByBlobUri(String blobUri);

    /**
     * Finds the top 100 queued jobs matching a specific status, ordered by creation time ascending.
     *
     * @param status the target job status
     * @return a list of matching queued ingestion jobs
     */
    List<IngestionJob> findTop100ByStatusOrderByCreatedAtAsc(String status);

    /**
     * Finds queued jobs matching a specific status with paging/limiting, ordered by creation time ascending.
     * Used for locking and batch processing by the worker.
     *
     * @param status the target job status
     * @param pageable page settings including size limits
     * @return a page list of ingestion jobs
     */
    List<IngestionJob> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    /**
     * Counts the total number of ingestion jobs matching a specific status.
     *
     * @param status the target job status
     * @return the count of matching jobs
     */
    long countByStatus(String status);

    /**
     * Finds all ingestion jobs matching a specific status.
     *
     * @param status the target job status
     * @return a list of matching ingestion jobs
     */
    List<IngestionJob> findByStatus(String status);
}

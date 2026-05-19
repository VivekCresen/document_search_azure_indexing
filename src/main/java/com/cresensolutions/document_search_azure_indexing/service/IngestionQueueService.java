package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.dto.BlobScanResult;

/**
 * Service interface for scanning storage containers and queueing documents
 * into the database for asynchronous indexing and processing.
 */
public interface IngestionQueueService {

    /**
     * Scans configured Azure Blob Storage directory, detects new/updated files,
     * and queues ingestion jobs for them.
     *
     * @return the result of the storage scan, including added and deleted counts
     */
    BlobScanResult scanAndQueue();

    /**
     * Requeues jobs that were marked stable but might need re-indexing or
     * validation.
     *
     * @return the number of jobs successfully requeued
     */
    int requeueStableFiles();

    /**
     * Manually queues a single blob for indexing by its URI and name details.
     *
     * @param blobUri the fully qualified URI of the target blob
     * @param blobName the storage relative name of the blob
     * @param fileName the clean local filename
     * @return true if the job was queued successfully, false otherwise
     */
    boolean queueSingleBlob(String blobUri, String blobName, String fileName);
}

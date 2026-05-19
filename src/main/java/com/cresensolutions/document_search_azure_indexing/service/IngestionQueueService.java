package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.dto.BlobScanResult;

public interface IngestionQueueService {
    BlobScanResult scanAndQueue();
    int requeueStableFiles();
    boolean queueSingleBlob(String blobUri, String blobName, String fileName);
}

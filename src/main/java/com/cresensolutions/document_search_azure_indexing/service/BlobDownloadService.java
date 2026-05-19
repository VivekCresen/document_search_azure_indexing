package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.dto.DownloadedBlob;

public interface BlobDownloadService {
    DownloadedBlob download(String blobUri, String knownBlobName, String knownFileName);
    void cleanup(DownloadedBlob downloadedBlob);
}

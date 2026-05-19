package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.dto.DownloadedBlob;

/**
 * Service interface for downloading blobs from Azure Storage to local disk
 * and performing cleanups after ingestion operations complete.
 */
public interface BlobDownloadService {

    /**
     * Downloads a remote blob into a temporary local file.
     *
     * @param blobUri the fully qualified URI of the target blob
     * @param knownBlobName optional storage path (will extract from URI if blank)
     * @param knownFileName optional local filename (will extract from path if blank)
     * @return a DownloadedBlob representing the downloaded local reference
     */
    DownloadedBlob download(String blobUri, String knownBlobName, String knownFileName);

    /**
     * Cleans up local temporary files and directories created during download.
     *
     * @param downloadedBlob the reference representing downloaded files to delete
     */
    void cleanup(DownloadedBlob downloadedBlob);
}

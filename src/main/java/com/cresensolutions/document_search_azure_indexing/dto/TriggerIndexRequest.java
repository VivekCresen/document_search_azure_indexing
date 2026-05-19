package com.cresensolutions.document_search_azure_indexing.dto;

/**
 * Request DTO for triggering indexing of a single blob.
 * Called by the Spring AI service immediately after a successful upload,
 * so that documents are indexed without waiting for the next scheduled scan.
 *
 * @param blobUri  Full Azure Blob URI  (e.g. https://account.blob.core.windows.net/container/path/file.pdf)
 * @param blobName Relative blob path inside the container (e.g. folder/uuid/file.pdf)
 * @param fileName Human-readable filename (e.g. file.pdf)
 */
public record TriggerIndexRequest(
        String blobUri,
        String blobName,
        String fileName
) {}

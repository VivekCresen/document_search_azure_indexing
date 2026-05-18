package com.cresensolutions.document_search_azure_indexing.dto;

import java.time.OffsetDateTime;

public record BlobInventoryItem(
        String blobUri,
        String blobName,
        String fileName,
        OffsetDateTime lastModified,
        Long sizeBytes,
        String etag
) {
}

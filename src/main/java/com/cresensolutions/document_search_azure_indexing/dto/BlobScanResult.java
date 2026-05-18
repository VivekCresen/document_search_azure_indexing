package com.cresensolutions.document_search_azure_indexing.dto;

import java.util.List;

public record BlobScanResult(
        int scanned,
        int queuedForIngestion,
        int queuedForDeletion,
        int unchanged,
        List<String> ingestionBlobUris,
        List<String> deletionBlobUris
) {
}

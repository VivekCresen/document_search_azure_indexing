package com.cresensolutions.document_search_azure_indexing.dto;

import java.nio.file.Path;

public record DownloadedBlob(
        String blobUri,
        String blobName,
        String fileName,
        Path path
) {
}

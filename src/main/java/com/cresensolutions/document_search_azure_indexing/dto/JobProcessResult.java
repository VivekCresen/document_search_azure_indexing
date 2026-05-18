package com.cresensolutions.document_search_azure_indexing.dto;

public record JobProcessResult(
        long processed,
        long succeeded,
        long failed
) {
}

package com.cresensolutions.document_search_azure_indexing.dto;

import java.util.List;

public record EnrichmentResult(
        List<String> topics,
        List<String> exampleQueries,
        List<String> intentSignals
) {

    public static EnrichmentResult empty() {
        return new EnrichmentResult(List.of(), List.of(), List.of("unknown"));
    }
}

package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.dto.EnrichmentResult;

public interface MetadataEnrichmentService {
    EnrichmentResult enrich(String chunkText);
}

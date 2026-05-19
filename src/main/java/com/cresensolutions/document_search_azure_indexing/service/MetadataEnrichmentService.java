package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.dto.EnrichmentResult;

/**
 * Service interface for enriching document chunks using LLM models.
 * Automatically identifies topics, signals, and example questions from chunks.
 */
public interface MetadataEnrichmentService {

    /**
     * Enriches a chunk of text by prompting the configured chat model to extract
     * primary topics, relevant intent signals, and suggested user queries.
     *
     * @param chunkText the text block to be enriched
     * @return the result holding topics, queries, and signals, or empty result if error/disabled
     */
    EnrichmentResult enrich(String chunkText);
}

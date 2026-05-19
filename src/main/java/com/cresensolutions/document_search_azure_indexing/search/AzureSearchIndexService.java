package com.cresensolutions.document_search_azure_indexing.search;

import java.util.List;
import java.util.Map;

/**
 * Service interface for interacting with the Azure Cognitive Search Index.
 * Handles schema creation/updates, uploading structured search documents, and deletion of indexed chunks.
 */
public interface AzureSearchIndexService {

    /**
     * Creates or updates the Azure Search index schema matching current configurations,
     * including cognitive vector profiles and semantic search models.
     */
    void createOrUpdateIndex();

    /**
     * Uploads a batch of generated and enriched document chunks to Azure Search.
     *
     * @param documents a list of document maps matching the search index schema
     */
    void uploadDocuments(List<Map<String, Object>> documents);

    /**
     * Deletes indexed document chunks from the Azure Search index by their search document IDs.
     *
     * @param documentIds a list of search document IDs to delete
     */
    void deleteDocumentsByIds(List<String> documentIds);
}

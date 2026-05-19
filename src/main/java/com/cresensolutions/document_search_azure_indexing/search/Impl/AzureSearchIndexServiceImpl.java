package com.cresensolutions.document_search_azure_indexing.search.Impl;

import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.search.AzureSearchIndexService;
import com.cresensolutions.document_search_azure_indexing.utils.CommonUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link AzureSearchIndexService} using Spring's RestClient
 * to communicate directly with Azure Cognitive Search REST API endpoints.
 */
@Service
public class AzureSearchIndexServiceImpl implements AzureSearchIndexService {

    private final AzureIndexingProperties properties;
    private final RestClient restClient;

    /**
     * Constructs the search service implementation.
     *
     * @param properties Azure configuration properties holding API keys and index names
     * @param restClientBuilder rest client builder helper
     */
    public AzureSearchIndexServiceImpl(AzureIndexingProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    /**
     * Ensures the search index matches the target configuration schema,
     * creating or upgrading the cognitive vector and indexing definitions.
     */
    @Override
    public void createOrUpdateIndex() {
        String url = "%s/indexes/%s?api-version=%s".formatted(
                CommonUtils.trimTrailingSlash(properties.search().endpoint()),
                properties.search().indexName(),
                properties.search().apiVersion()
        );
        restClient.put()
                .uri(url)
                .header("api-key", properties.search().apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(indexSchema())
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Uploads and indexes a list of parsed/processed document chunks to Azure Cognitive Search.
     * Enhances each document with `@search.action: upload` metadata as required by Azure.
     *
     * @param documents list of maps representing the document fields to upload
     */
    @Override
    public void uploadDocuments(List<Map<String, Object>> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        // Map each document to include the upload action header for Azure indexer
        List<Map<String, Object>> actions = documents.stream()
                .<Map<String, Object>>map(document -> {
                    java.util.LinkedHashMap<String, Object> action = new java.util.LinkedHashMap<>(document);
                    action.put("@search.action", "upload");
                    return action;
                })
                .toList();
        postIndexActions(actions);
    }

    /**
     * Deletes indexed document chunks from Azure Cognitive Search by their unique identifiers.
     *
     * @param documentIds list of document IDs to delete from the index
     */
    @Override
    public void deleteDocumentsByIds(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        // Transform IDs to Azure search delete actions
        postIndexActions(documentIds.stream()
                .map(id -> Map.<String, Object>of("@search.action", "delete", "id", id))
                .toList());
    }

    /**
     * Sends action requests (upload/delete) in batches to the Azure search service endpoint
     * to avoid size limit exceptions or timeouts.
     *
     * @param actions list of action maps (either upload or delete actions)
     */
    private void postIndexActions(List<Map<String, Object>> actions) {
        int batchSize = Math.max(1, properties.settings().batchSize());
        // Batch and send documents sequentially based on settings config
        for (int start = 0; start < actions.size(); start += batchSize) {
            int end = Math.min(actions.size(), start + batchSize);
            String url = "%s/indexes/%s/docs/index?api-version=%s".formatted(
                    CommonUtils.trimTrailingSlash(properties.search().endpoint()),
                    properties.search().indexName(),
                    properties.search().apiVersion()
            );
            restClient.post()
                    .uri(url)
                    .header("api-key", properties.search().apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("value", actions.subList(start, end)))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    /**
     * Builds and returns the JSON-compatible schema definition for the Azure Cognitive Search index.
     * Defines fields, data types, key constraints, HNSW vector search algorithm profile, and semantic settings.
     *
     * @return the map representing Azure index schema configuration
     */
    private Map<String, Object> indexSchema() {
        return Map.of(
                "name", properties.search().indexName(),
                "fields", List.of(
                        // Document unique key
                        Map.of("name", "id", "type", "Edm.String", "key", true, "filterable", true),
                        // Extracted text content using English analyzer
                        Map.of("name", "content", "type", "Edm.String", "searchable", true, "analyzer", "en.microsoft"),
                        Map.of("name", "title", "type", "Edm.String", "searchable", true),
                        Map.of("name", "source", "type", "Edm.String", "filterable", true),
                        Map.of("name", "filepath", "type", "Edm.String", "filterable", true),
                        Map.of("name", "file_type", "type", "Edm.String", "filterable", true),
                        Map.of("name", "chunk_id", "type", "Edm.String"),
                        Map.of("name", "page_number", "type", "Edm.Int32", "filterable", true),
                        Map.of("name", "created_at", "type", "Edm.DateTimeOffset", "filterable", true),
                        Map.of("name", "metadata", "type", "Edm.String", "searchable", true),
                        // Collection fields for categorization and search refinement
                        Map.of("name", "topics", "type", "Collection(Edm.String)", "searchable", true, "filterable", true),
                        Map.of("name", "example_queries", "type", "Collection(Edm.String)", "searchable", true),
                        Map.of("name", "intent_signals", "type", "Collection(Edm.String)", "searchable", true, "filterable", true),
                        Map.of("name", "folder_id", "type", "Edm.String", "filterable", true, "retrievable", true),
                        Map.of("name", "blob_uri", "type", "Edm.String", "filterable", true, "retrievable", true),
                        // Vector search field configuration (e.g. text-embedding-ada-002 dimensions)
                        Map.of(
                                "name", "embedding",
                                "type", "Collection(Edm.Single)",
                                "searchable", true,
                                "dimensions", 1536,
                                "vectorSearchProfile", "my-vector-profile"
                        )
                ),
                // Configure Vector Search Profile with HNSW algorithm
                "vectorSearch", Map.of(
                        "algorithms", List.of(Map.of("name", "my-hnsw-config", "kind", "hnsw")),
                        "profiles", List.of(Map.of(
                                "name", "my-vector-profile",
                                "algorithm", "my-hnsw-config"
                        ))
                ),
                // Configure Semantic Ranking prioritizing title and content
                "semantic", Map.of(
                        "configurations", List.of(Map.of(
                                "name", "my-semantic-config",
                                "prioritizedFields", Map.of(
                                        "titleField", Map.of("fieldName", "title"),
                                        "prioritizedContentFields", List.of(Map.of("fieldName", "content")),
                                        "prioritizedKeywordsFields", List.of(
                                                Map.of("fieldName", "topics"),
                                                Map.of("fieldName", "intent_signals")
                                        )
                                )
                        ))
                )
        );
    }
}

package com.cresensolutions.document_search_azure_indexing.search;

import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class AzureSearchIndexService {

    private final AzureIndexingProperties properties;
    private final RestClient restClient;

    public AzureSearchIndexService(AzureIndexingProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    public void createOrUpdateIndex() {
        String url = "%s/indexes/%s?api-version=%s".formatted(
                properties.search().endpoint().replaceAll("/$", ""),
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

    public void uploadDocuments(List<Map<String, Object>> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        List<Map<String, Object>> actions = documents.stream()
                .<Map<String, Object>>map(document -> {
                    java.util.LinkedHashMap<String, Object> action = new java.util.LinkedHashMap<>(document);
                    action.put("@search.action", "upload");
                    return action;
                })
                .toList();
        postIndexActions(actions);
    }

    public void deleteDocumentsByIds(List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        postIndexActions(documentIds.stream()
                .map(id -> Map.<String, Object>of("@search.action", "delete", "id", id))
                .toList());
    }

    private void postIndexActions(List<Map<String, Object>> actions) {
        int batchSize = Math.max(1, properties.settings().batchSize());
        for (int start = 0; start < actions.size(); start += batchSize) {
            int end = Math.min(actions.size(), start + batchSize);
            String url = "%s/indexes/%s/docs/index?api-version=%s".formatted(
                    properties.search().endpoint().replaceAll("/$", ""),
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

    private Map<String, Object> indexSchema() {
        return Map.of(
                "name", properties.search().indexName(),
                "fields", List.of(
                        Map.of("name", "id", "type", "Edm.String", "key", true, "filterable", true),
                        Map.of("name", "content", "type", "Edm.String", "searchable", true, "analyzer", "en.microsoft"),
                        Map.of("name", "title", "type", "Edm.String", "searchable", true),
                        Map.of("name", "source", "type", "Edm.String", "filterable", true),
                        Map.of("name", "filepath", "type", "Edm.String", "filterable", true),
                        Map.of("name", "file_type", "type", "Edm.String", "filterable", true),
                        Map.of("name", "chunk_id", "type", "Edm.String"),
                        Map.of("name", "page_number", "type", "Edm.Int32", "filterable", true),
                        Map.of("name", "created_at", "type", "Edm.DateTimeOffset", "filterable", true),
                        Map.of("name", "metadata", "type", "Edm.String", "searchable", true),
                        Map.of("name", "topics", "type", "Collection(Edm.String)", "searchable", true, "filterable", true),
                        Map.of("name", "example_queries", "type", "Collection(Edm.String)", "searchable", true),
                        Map.of("name", "intent_signals", "type", "Collection(Edm.String)", "searchable", true, "filterable", true),
                        Map.of("name", "folder_id", "type", "Edm.String", "filterable", true, "retrievable", true),
                        Map.of("name", "blob_uri", "type", "Edm.String", "filterable", true, "retrievable", true),
                        Map.of(
                                "name", "embedding",
                                "type", "Collection(Edm.Single)",
                                "searchable", true,
                                "dimensions", 1536,
                                "vectorSearchProfile", "my-vector-profile"
                        )
                ),
                "vectorSearch", Map.of(
                        "algorithms", List.of(Map.of("name", "my-hnsw-config", "kind", "hnsw")),
                        "profiles", List.of(Map.of(
                                "name", "my-vector-profile",
                                "algorithm", "my-hnsw-config"
                        ))
                ),
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

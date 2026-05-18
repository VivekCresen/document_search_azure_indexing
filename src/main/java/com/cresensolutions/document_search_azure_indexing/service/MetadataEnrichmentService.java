package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.dto.EnrichmentResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class MetadataEnrichmentService {

    private final AzureIndexingProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public MetadataEnrichmentService(
            AzureIndexingProperties properties,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    public EnrichmentResult enrich(String chunkText) {
        if (!StringUtils.hasText(properties.openAi().endpoint())
                || !StringUtils.hasText(properties.openAi().apiKey())
                || !StringUtils.hasText(properties.openAi().chatDeployment())) {
            return EnrichmentResult.empty();
        }

        String prompt = """
                Analyze this document chunk and generate metadata to improve search and classification.

                Return only JSON:
                {
                  "topics": ["3-5 primary keywords"],
                  "example_queries": ["3 realistic user questions this chunk can answer"],
                  "intent_signals": ["Policy Inquiry" | "Procedural Guide" | "Contact Information" | "Definition/Explanation" | "Data Request"]
                }

                DOCUMENT CHUNK:
                ---
                %s
                ---
                """.formatted(chunkText.substring(0, Math.min(chunkText.length(), 2000)));

        try {
            String url = "%s/openai/deployments/%s/chat/completions?api-version=%s".formatted(
                    properties.openAi().endpoint().replaceAll("/$", ""),
                    properties.openAi().chatDeployment(),
                    properties.openAi().apiVersion()
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(url)
                    .header("api-key", properties.openAi().apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "messages", List.of(Map.of("role", "user", "content", prompt)),
                            "response_format", Map.of("type", "json_object")
                    ))
                    .retrieve()
                    .body(Map.class);

            String content = extractContent(response);
            if (!StringUtils.hasText(content)) {
                return EnrichmentResult.empty();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(content, Map.class);
            return new EnrichmentResult(
                    stringList(parsed.get("topics")),
                    stringList(parsed.get("example_queries")),
                    stringList(parsed.get("intent_signals"))
            );
        } catch (Exception ignored) {
            return EnrichmentResult.empty();
        }
    }

    private String extractContent(Map<String, Object> response) {
        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            return "";
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choice) || !(choice.get("message") instanceof Map<?, ?> message)) {
            return "";
        }
        Object content = message.containsKey("content") ? message.get("content") : "";
        return String.valueOf(content);
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        if (value == null) {
            return List.of();
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? List.of(text) : List.of();
    }
}

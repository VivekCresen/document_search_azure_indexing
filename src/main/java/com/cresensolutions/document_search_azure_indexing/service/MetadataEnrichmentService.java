package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.dto.EnrichmentResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
public class MetadataEnrichmentService {

    private final AzureIndexingProperties properties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ChatModel> chatModelProvider;

    public MetadataEnrichmentService(
            AzureIndexingProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<ChatModel> chatModelProvider
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.chatModelProvider = chatModelProvider;
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
            ChatModel chatModel = chatModelProvider.getIfAvailable();
            if (chatModel == null) {
                return EnrichmentResult.empty();
            }
            ChatResponse response = chatModel.call(new Prompt(prompt));
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

    private String extractContent(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
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

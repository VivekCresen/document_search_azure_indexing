package com.cresensolutions.document_search_azure_indexing.service.Impl;

import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.dto.EnrichmentResult;
import com.cresensolutions.document_search_azure_indexing.service.MetadataEnrichmentService;
import com.cresensolutions.document_search_azure_indexing.utils.CommonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link MetadataEnrichmentService} that prompts Spring AI's ChatModel
 * to extract intents, primary topics, and relevant search queries from chunk texts.
 */
@Service
@RequiredArgsConstructor
public class MetadataEnrichmentServiceImpl implements MetadataEnrichmentService {

    private final AzureIndexingProperties properties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ChatModel> chatModelProvider;

    /**
     * Extracts cognitive topic tags, intent signals, and suggested user queries for a given chunk
     * via LLM prompt. Falls back to empty values if AI is disabled or fails.
     *
     * @param chunkText the text block to be enriched
     * @return the EnrichmentResult containing lists of topics, queries, and intent signals
     */
    @Override
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
            Map<String, Object> parsed = CommonUtils.parseLlmJson(content, Map.class, objectMapper);
            return new EnrichmentResult(
                    stringList(parsed.get("topics")),
                    stringList(parsed.get("example_queries")),
                    stringList(parsed.get("intent_signals"))
            );
        } catch (Exception ignored) {
            return EnrichmentResult.empty();
        }
    }

    /**
     * Safely pulls the raw text response content from ChatResponse.
     */
    private String extractContent(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    /**
     * Coerces any parsed JSON list or single value into a clean, trimmed String list.
     */
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

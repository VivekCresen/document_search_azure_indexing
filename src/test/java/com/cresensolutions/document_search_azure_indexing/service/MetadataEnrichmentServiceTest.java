package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.service.Impl.MetadataEnrichmentServiceImpl;
import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.dto.EnrichmentResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataEnrichmentServiceTest {

    @Mock
    private AzureIndexingProperties properties;

    @Mock
    private AzureIndexingProperties.OpenAi openAi;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ObjectProvider<ChatModel> chatModelProvider;

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private MetadataEnrichmentServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(properties.openAi()).thenReturn(openAi);
    }

    @Test
    void testEnrichWithMissingProperties() {
        when(openAi.endpoint()).thenReturn("");
        EnrichmentResult result = service.enrich("chunkText");
        assertEquals(EnrichmentResult.empty(), result);

        when(openAi.endpoint()).thenReturn("http://openai");
        when(openAi.apiKey()).thenReturn("");
        result = service.enrich("chunkText");
        assertEquals(EnrichmentResult.empty(), result);

        when(openAi.apiKey()).thenReturn("key");
        when(openAi.chatDeployment()).thenReturn(null);
        result = service.enrich("chunkText");
        assertEquals(EnrichmentResult.empty(), result);
    }

    @Test
    void testEnrichWithNullChatModel() {
        when(openAi.endpoint()).thenReturn("http://openai");
        when(openAi.apiKey()).thenReturn("key");
        when(openAi.chatDeployment()).thenReturn("chat-dep");
        when(chatModelProvider.getIfAvailable()).thenReturn(null);

        EnrichmentResult result = service.enrich("chunkText");
        assertEquals(EnrichmentResult.empty(), result);
    }

    @Test
    void testEnrichWithEmptyResponse() {
        when(openAi.endpoint()).thenReturn("http://openai");
        when(openAi.apiKey()).thenReturn("key");
        when(openAi.chatDeployment()).thenReturn("chat-dep");
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(null);

        EnrichmentResult result = service.enrich("chunkText");
        assertEquals(EnrichmentResult.empty(), result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testEnrichSuccess() throws Exception {
        when(openAi.endpoint()).thenReturn("http://openai");
        when(openAi.apiKey()).thenReturn("key");
        when(openAi.chatDeployment()).thenReturn("chat-dep");
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);

        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = mock(AssistantMessage.class);

        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getText()).thenReturn("{\"topics\": [\"topic1\"], \"example_queries\": \"query1\", \"intent_signals\": null}");

        Map<String, Object> mockParsed = Map.of(
                "topics", List.of("topic1"),
                "example_queries", "query1",
                "intent_signals", "   "
        );
        when(objectMapper.readValue(eq("{\"topics\": [\"topic1\"], \"example_queries\": \"query1\", \"intent_signals\": null}"), eq(Map.class)))
                .thenReturn(mockParsed);

        EnrichmentResult result = service.enrich("chunkText");

        assertNotNull(result);
        assertEquals(List.of("topic1"), result.topics());
        assertEquals(List.of("query1"), result.exampleQueries());
        assertTrue(result.intentSignals().isEmpty());
    }

    @Test
    void testEnrichParsingExceptionReturnsEmpty() throws Exception {
        when(openAi.endpoint()).thenReturn("http://openai");
        when(openAi.apiKey()).thenReturn("key");
        when(openAi.chatDeployment()).thenReturn("chat-dep");
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);

        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = mock(AssistantMessage.class);

        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getText()).thenReturn("invalid-json");

        when(objectMapper.readValue(anyString(), eq(Map.class))).thenThrow(new RuntimeException("JSON error"));

        EnrichmentResult result = service.enrich("chunkText");
        assertEquals(EnrichmentResult.empty(), result);
    }
}

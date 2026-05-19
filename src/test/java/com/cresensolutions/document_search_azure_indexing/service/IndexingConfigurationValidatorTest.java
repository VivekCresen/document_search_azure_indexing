package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.service.Impl.IndexingConfigurationValidatorImpl;
import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class IndexingConfigurationValidatorTest {

    @Mock
    private AzureIndexingProperties properties;

    @Mock
    private AzureIndexingProperties.Search search;

    @Mock
    private AzureIndexingProperties.OpenAi openAi;

    @Mock
    private AzureIndexingProperties.Storage storage;

    @Mock
    private AzureIndexingProperties.DocumentIntelligence documentIntelligence;

    @InjectMocks
    private IndexingConfigurationValidatorImpl validator;

    @Test
    void testValidateWithValidConnectionString() {
        lenient().when(properties.search()).thenReturn(search);
        lenient().when(properties.openAi()).thenReturn(openAi);
        lenient().when(properties.storage()).thenReturn(storage);
        lenient().when(properties.documentIntelligence()).thenReturn(documentIntelligence);

        lenient().when(search.endpoint()).thenReturn("http://search-endpoint");
        lenient().when(search.apiKey()).thenReturn("search-key");
        lenient().when(search.indexName()).thenReturn("search-index");

        lenient().when(openAi.endpoint()).thenReturn("http://openai-endpoint");
        lenient().when(openAi.apiKey()).thenReturn("openai-key");
        lenient().when(openAi.embeddingDeployment()).thenReturn("emb-dep");
        lenient().when(openAi.chatDeployment()).thenReturn("chat-dep");

        lenient().when(storage.containerName()).thenReturn("container");
        lenient().when(storage.connectionString()).thenReturn("UseDevelopmentStorage=true");

        lenient().when(documentIntelligence.endpoint()).thenReturn("http://di-endpoint");
        lenient().when(documentIntelligence.apiKey()).thenReturn("di-key");

        Map<String, Object> result = validator.validate();

        assertTrue((Boolean) result.get("valid"));
        assertTrue(((List<?>) result.get("missing")).isEmpty());
        assertTrue((Boolean) result.get("documentIntelligenceConfigured"));
    }

    @Test
    void testValidateWithValidAccountCredentials() {
        lenient().when(properties.search()).thenReturn(search);
        lenient().when(properties.openAi()).thenReturn(openAi);
        lenient().when(properties.storage()).thenReturn(storage);
        lenient().when(properties.documentIntelligence()).thenReturn(documentIntelligence);

        lenient().when(search.endpoint()).thenReturn("http://search-endpoint");
        lenient().when(search.apiKey()).thenReturn("search-key");
        lenient().when(search.indexName()).thenReturn("search-index");

        lenient().when(openAi.endpoint()).thenReturn("http://openai-endpoint");
        lenient().when(openAi.apiKey()).thenReturn("openai-key");
        lenient().when(openAi.embeddingDeployment()).thenReturn("emb-dep");
        lenient().when(openAi.chatDeployment()).thenReturn("chat-dep");

        lenient().when(storage.containerName()).thenReturn("container");
        lenient().when(storage.connectionString()).thenReturn("");
        lenient().when(storage.accountName()).thenReturn("storage-account");
        lenient().when(storage.accountKey()).thenReturn("storage-key");

        lenient().when(documentIntelligence.endpoint()).thenReturn("");
        lenient().when(documentIntelligence.apiKey()).thenReturn("");

        Map<String, Object> result = validator.validate();

        assertTrue((Boolean) result.get("valid"));
        assertTrue(((List<?>) result.get("missing")).isEmpty());
        assertFalse((Boolean) result.get("documentIntelligenceConfigured"));
    }

    @Test
    void testValidateWithMissingProperties() {
        lenient().when(properties.search()).thenReturn(search);
        lenient().when(properties.openAi()).thenReturn(openAi);
        lenient().when(properties.storage()).thenReturn(storage);
        lenient().when(properties.documentIntelligence()).thenReturn(documentIntelligence);

        // Missing search endpoint and openAi key, also missing accountName/accountKey since connectionString is empty
        lenient().when(search.endpoint()).thenReturn("");
        lenient().when(search.apiKey()).thenReturn("search-key");
        lenient().when(search.indexName()).thenReturn("search-index");

        lenient().when(openAi.endpoint()).thenReturn("http://openai-endpoint");
        lenient().when(openAi.apiKey()).thenReturn("");
        lenient().when(openAi.embeddingDeployment()).thenReturn("emb-dep");
        lenient().when(openAi.chatDeployment()).thenReturn("chat-dep");

        lenient().when(storage.containerName()).thenReturn("");
        lenient().when(storage.connectionString()).thenReturn(null);
        lenient().when(storage.accountName()).thenReturn("");
        lenient().when(storage.accountKey()).thenReturn("");

        lenient().when(documentIntelligence.endpoint()).thenReturn(null);
        lenient().when(documentIntelligence.apiKey()).thenReturn(null);

        Map<String, Object> result = validator.validate();

        assertFalse((Boolean) result.get("valid"));
        List<?> missing = (List<?>) result.get("missing");
        assertTrue(missing.contains("AZURE_SEARCH_ENDPOINT"));
        assertTrue(missing.contains("AZURE_OPENAI_KEY"));
        assertTrue(missing.contains("AZURE_STORAGE_CONTAINER_NAME"));
        assertTrue(missing.contains("AZURE_STORAGE_ACCOUNT_NAME"));
        assertTrue(missing.contains("AZURE_STORAGE_ACCOUNT_KEY"));
        assertFalse((Boolean) result.get("documentIntelligenceConfigured"));
    }
}

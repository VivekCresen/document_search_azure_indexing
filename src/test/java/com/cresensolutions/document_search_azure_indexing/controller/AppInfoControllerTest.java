package com.cresensolutions.document_search_azure_indexing.controller;

import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.service.IndexingConfigurationValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppInfoControllerTest {

    @Mock
    private AzureIndexingProperties properties;

    @Mock
    private AzureIndexingProperties.Search search;

    @Mock
    private AzureIndexingProperties.Storage storage;

    @Mock
    private AzureIndexingProperties.Settings settings;

    @Mock
    private IndexingConfigurationValidator validator;

    @InjectMocks
    private AppInfoController controller;

    @Test
    void testInfoWithNonNullValues() {
        when(properties.search()).thenReturn(search);
        when(properties.storage()).thenReturn(storage);
        when(properties.settings()).thenReturn(settings);

        when(search.indexName()).thenReturn("my-index");
        when(storage.containerName()).thenReturn("my-container");
        when(settings.chunkSize()).thenReturn(1000);
        when(settings.batchSize()).thenReturn(50);
        
        Map<String, Object> configMap = Map.of("valid", true, "missing", List.of());
        when(validator.validate()).thenReturn(configMap);

        Map<String, Object> result = controller.info();

        assertEquals("document-search-azure-indexing", result.get("service"));
        assertEquals("my-index", result.get("searchIndex"));
        assertEquals("my-container", result.get("container"));
        assertEquals(1000, result.get("chunkSize"));
        assertEquals(50, result.get("batchSize"));
        assertEquals(configMap, result.get("configuration"));
    }

    @Test
    void testInfoWithNullValues() {
        when(properties.search()).thenReturn(search);
        when(properties.storage()).thenReturn(storage);
        when(properties.settings()).thenReturn(settings);

        when(search.indexName()).thenReturn(null);
        when(storage.containerName()).thenReturn(null);
        when(settings.chunkSize()).thenReturn(1000);
        when(settings.batchSize()).thenReturn(50);
        
        Map<String, Object> configMap = Map.of("valid", false, "missing", List.of("ERROR"));
        when(validator.validate()).thenReturn(configMap);

        Map<String, Object> result = controller.info();

        assertEquals("", result.get("searchIndex"));
        assertEquals("", result.get("container"));
    }
}

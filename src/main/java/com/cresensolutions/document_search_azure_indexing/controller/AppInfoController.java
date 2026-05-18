package com.cresensolutions.document_search_azure_indexing.controller;

import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.service.IndexingConfigurationValidator;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/indexing")
public class AppInfoController {

    private final AzureIndexingProperties properties;
    private final IndexingConfigurationValidator validator;

    public AppInfoController(AzureIndexingProperties properties, IndexingConfigurationValidator validator) {
        this.properties = properties;
        this.validator = validator;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "service", "document-search-azure-indexing",
                "searchIndex", valueOrEmpty(properties.search().indexName()),
                "container", valueOrEmpty(properties.storage().containerName()),
                "chunkSize", properties.settings().chunkSize(),
                "batchSize", properties.settings().batchSize(),
                "configuration", validator.validate()
        );
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}

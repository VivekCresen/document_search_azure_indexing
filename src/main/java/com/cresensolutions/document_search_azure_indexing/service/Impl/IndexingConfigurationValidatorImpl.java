package com.cresensolutions.document_search_azure_indexing.service.Impl;

import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.service.IndexingConfigurationValidator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IndexingConfigurationValidatorImpl implements IndexingConfigurationValidator {

    private final AzureIndexingProperties properties;

    @Override
    public Map<String, Object> validate() {
        List<String> missing = new ArrayList<>();

        require(missing, properties.search().endpoint(), "AZURE_SEARCH_ENDPOINT");
        require(missing, properties.search().apiKey(), "AZURE_SEARCH_ADMIN_KEY or AZURE_SEARCH_KEY");
        require(missing, properties.search().indexName(), "AZURE_SEARCH_INDEX_NAME");

        require(missing, properties.openAi().endpoint(), "AZURE_OPENAI_ENDPOINT");
        require(missing, properties.openAi().apiKey(), "AZURE_OPENAI_KEY");
        require(missing, properties.openAi().embeddingDeployment(), "AZURE_OPENAI_EMBEDDING_DEPLOYMENT");
        require(missing, properties.openAi().chatDeployment(), "AZURE_OPENAI_DEPLOYMENT");

        require(missing, properties.storage().containerName(), "AZURE_STORAGE_CONTAINER_NAME");
        if (!StringUtils.hasText(properties.storage().connectionString())) {
            require(missing, properties.storage().accountName(), "AZURE_STORAGE_ACCOUNT_NAME");
            require(missing, properties.storage().accountKey(), "AZURE_STORAGE_ACCOUNT_KEY");
        }

        return Map.of(
                "valid", missing.isEmpty(),
                "missing", missing,
                "documentIntelligenceConfigured", StringUtils.hasText(properties.documentIntelligence().endpoint())
                        && StringUtils.hasText(properties.documentIntelligence().apiKey())
        );
    }

    private void require(List<String> missing, String value, String name) {
        if (!StringUtils.hasText(value)) {
            missing.add(name);
        }
    }
}

package com.cresensolutions.document_search_azure_indexing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "azure.indexing")
public record AzureIndexingProperties(
        Search search,
        Storage storage,
        OpenAi openAi,
        DocumentIntelligence documentIntelligence,
        Settings settings
) {

    public record Search(
            String endpoint,
            String apiKey,
            String indexName,
            String apiVersion
    ) {
    }

    public record Storage(
            String accountName,
            String accountKey,
            String accountUrl,
            String connectionString,
            String containerName,
            String directory
    ) {
    }

    public record OpenAi(
            String endpoint,
            String apiKey,
            String embeddingDeployment,
            String chatDeployment,
            String apiVersion
    ) {
    }

    public record DocumentIntelligence(
            String endpoint,
            String apiKey,
            String apiVersion
    ) {
    }

    public record Settings(
            Integer chunkSize,
            Integer chunkOverlap,
            Integer batchSize,
            Integer maxRetries,
            Boolean blobScanEnabled,
            Long blobScanFixedDelayMs,
            Boolean jobProcessingEnabled,
            Long jobProcessingFixedDelayMs,
            Integer maxJobsPerCycle,
            List<String> supportedExtensions
    ) {
    }
}

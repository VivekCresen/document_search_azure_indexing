package com.cresensolutions.document_search_azure_indexing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Strongly-typed ConfigurationProperties record mapping "azure.indexing" parameters
 * from configuration files (application.yaml). Covers cognitive search, storage accounts,
 * OpenAI models, Document Intelligence API, and scheduling setting parameters.
 */
@ConfigurationProperties(prefix = "azure.indexing")
public record AzureIndexingProperties(
        /** Cognitive Search index definitions and endpoint details */
        Search search,
        /** Azure storage account credentials and target directory pathing */
        Storage storage,
        /** OpenAI endpoints, embeddings, and chat deployment models */
        OpenAi openAi,
        /** OCR layout models, api keys, and endpoint variables */
        DocumentIntelligence documentIntelligence,
        /** Microservice runtime settings including chunk sizes, thread batch sizes, and delay cycles */
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

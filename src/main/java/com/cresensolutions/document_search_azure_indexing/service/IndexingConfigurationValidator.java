package com.cresensolutions.document_search_azure_indexing.service;

import java.util.Map;

/**
 * Service interface for validating microservice settings, database connectivity,
 * AI models, and Azure cloud integration endpoints at startup or runtime.
 */
public interface IndexingConfigurationValidator {

    /**
     * Executes validation checks on system configurations, environment variables,
     * database status, Azure Blob connection, and Spring AI chat/embedding model availability.
     *
     * @return a Map containing status details for each configuration component
     */
    Map<String, Object> validate();
}

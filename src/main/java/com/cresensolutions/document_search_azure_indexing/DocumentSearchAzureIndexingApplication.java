package com.cresensolutions.document_search_azure_indexing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot Application Entrypoint for Document Search Azure Indexing service.
 * Enables caching, scheduling, and configuration properties scanning.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
@EnableScheduling
public class DocumentSearchAzureIndexingApplication {

    /**
     * Entry point of the Spring Boot application.
     *
     * @param args command line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(DocumentSearchAzureIndexingApplication.class, args);
    }
}

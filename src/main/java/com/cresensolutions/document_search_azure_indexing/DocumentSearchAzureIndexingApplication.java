package com.cresensolutions.document_search_azure_indexing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
@EnableScheduling
public class DocumentSearchAzureIndexingApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentSearchAzureIndexingApplication.class, args);
    }
}

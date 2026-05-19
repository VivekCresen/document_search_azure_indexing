package com.cresensolutions.document_search_azure_indexing.config;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Configuration class that instantiates and registers the Azure SDK {@link BlobContainerClient}
 * bean, using either a direct connection string or custom account/key credentials.
 */
@Configuration
public class AzureBlobClientConfig {

    /**
     * Creates and registers the Azure storage blob container client based on configuration.
     *
     * @param properties Azure configuration properties holding credentials and URLs
     * @return the BlobContainerClient instance connected to the designated storage container
     */
    @Bean
    BlobContainerClient indexingBlobContainerClient(AzureIndexingProperties properties) {
        AzureIndexingProperties.Storage storage = properties.storage();
        BlobServiceClientBuilder builder = new BlobServiceClientBuilder();

        if (StringUtils.hasText(storage.connectionString())) {
            builder.connectionString(storage.connectionString());
        } else {
            String endpoint = StringUtils.hasText(storage.accountUrl())
                    ? storage.accountUrl()
                    : "https://" + storage.accountName() + ".blob.core.windows.net/";
            builder.endpoint(endpoint)
                    .credential(new StorageSharedKeyCredential(storage.accountName(), stripWrappingQuotes(storage.accountKey())));
        }

        BlobServiceClient serviceClient = builder.buildClient();
        return serviceClient.getBlobContainerClient(storage.containerName());
    }

    private String stripWrappingQuotes(String value) {
        if (value != null && value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}

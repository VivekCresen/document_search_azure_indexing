package com.cresensolutions.document_search_azure_indexing.config;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class AzureBlobClientConfig {

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
                    .credential(new StorageSharedKeyCredential(storage.accountName(), storage.accountKey()));
        }

        BlobServiceClient serviceClient = builder.buildClient();
        return serviceClient.getBlobContainerClient(storage.containerName());
    }
}

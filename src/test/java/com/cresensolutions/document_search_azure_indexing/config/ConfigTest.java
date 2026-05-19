package com.cresensolutions.document_search_azure_indexing.config;

import com.azure.storage.blob.BlobContainerClient;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @Test
    void testAzureIndexingProperties() {
        AzureIndexingProperties.Search search = new AzureIndexingProperties.Search("end", "key", "index", "v1");
        AzureIndexingProperties.Storage storage = new AzureIndexingProperties.Storage("name", "key", "url", "conn", "container", "dir");
        AzureIndexingProperties.OpenAi openAi = new AzureIndexingProperties.OpenAi("end", "key", "emb", "chat", "v1");
        AzureIndexingProperties.DocumentIntelligence di = new AzureIndexingProperties.DocumentIntelligence("end", "key", "v1");
        AzureIndexingProperties.Settings settings = new AzureIndexingProperties.Settings(1, 2, 3, 4, true, 5L, true, 6L, 7, List.of("pdf"));

        AzureIndexingProperties props = new AzureIndexingProperties(search, storage, openAi, di, settings);

        assertEquals(search, props.search());
        assertEquals(storage, props.storage());
        assertEquals(openAi, props.openAi());
        assertEquals(di, props.documentIntelligence());
        assertEquals(settings, props.settings());
    }

    @Test
    void testAzureBlobClientConfigWithConnectionString() {
        AzureBlobClientConfig config = new AzureBlobClientConfig();
        AzureIndexingProperties.Storage storage = new AzureIndexingProperties.Storage(
                null, null, null, "UseDevelopmentStorage=true", "test-container", null
        );
        AzureIndexingProperties props = new AzureIndexingProperties(null, storage, null, null, null);

        BlobContainerClient containerClient = config.indexingBlobContainerClient(props);
        assertNotNull(containerClient);
        assertEquals("test-container", containerClient.getBlobContainerName());
    }

    @Test
    void testAzureBlobClientConfigWithAccountCredentials() {
        AzureBlobClientConfig config = new AzureBlobClientConfig();
        // A valid base64 account key is needed to avoid IllegalArgumentException during StorageSharedKeyCredential creation
        String validBase64Key = "dGVzdC1iYXNlNjQta2V5LXdoaWNoLWlzLWR1bW15LXRvLXBhc3MtYXV0aGVudGljYXRpb24=";
        AzureIndexingProperties.Storage storage = new AzureIndexingProperties.Storage(
                "cresengpt", validBase64Key, "https://cresengpt.blob.core.windows.net/", null, "test-container", null
        );
        AzureIndexingProperties props = new AzureIndexingProperties(null, storage, null, null, null);

        BlobContainerClient containerClient = config.indexingBlobContainerClient(props);
        assertNotNull(containerClient);
        assertEquals("test-container", containerClient.getBlobContainerName());
    }

    @Test
    void testAzureBlobClientConfigWithFallbackAccountUrl() {
        AzureBlobClientConfig config = new AzureBlobClientConfig();
        String validBase64Key = "dGVzdC1iYXNlNjQta2V5LXdoaWNoLWlzLWR1bW15LXRvLXBhc3MtYXV0aGVudGljYXRpb24=";
        // Testing empty account url path to hit lines 22-24 fallback
        AzureIndexingProperties.Storage storage = new AzureIndexingProperties.Storage(
                "cresengpt", validBase64Key, null, null, "test-container", null
        );
        AzureIndexingProperties props = new AzureIndexingProperties(null, storage, null, null, null);

        BlobContainerClient containerClient = config.indexingBlobContainerClient(props);
        assertNotNull(containerClient);
        assertEquals("test-container", containerClient.getBlobContainerName());
    }

    @Test
    void testCacheConfig() {
        CacheConfig config = new CacheConfig();
        CacheManager cacheManager = config.cacheManager();
        assertNotNull(cacheManager);
        assertNotNull(cacheManager.getCache("folderResolution"));
    }
}

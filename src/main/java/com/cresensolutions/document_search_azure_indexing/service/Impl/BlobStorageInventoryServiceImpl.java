package com.cresensolutions.document_search_azure_indexing.service.Impl;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.dto.BlobInventoryItem;
import com.cresensolutions.document_search_azure_indexing.service.BlobStorageInventoryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BlobStorageInventoryServiceImpl implements BlobStorageInventoryService {

    private final BlobContainerClient containerClient;
    private final AzureIndexingProperties properties;

    @Override
    public List<BlobInventoryItem> listIndexableBlobs() {
        String prefix = properties.storage().directory();
        List<String> supportedExtensions = properties.settings().supportedExtensions().stream()
                .map(extension -> extension.toLowerCase(Locale.ROOT))
                .toList();

        return containerClient.listBlobs()
                .stream()
                .filter(blob -> isInConfiguredDirectory(blob, prefix))
                .filter(blob -> isIndexable(blob, supportedExtensions))
                .map(this::toInventoryItem)
                .toList();
    }

    private boolean isInConfiguredDirectory(BlobItem blob, String prefix) {
        return !StringUtils.hasText(prefix) || blob.getName().startsWith(prefix);
    }

    private boolean isIndexable(BlobItem blob, List<String> supportedExtensions) {
        String lowerName = blob.getName().toLowerCase(Locale.ROOT);
        if (lowerName.contains("highlighted")) {
            return false;
        }
        return supportedExtensions.stream().anyMatch(lowerName::endsWith);
    }

    private BlobInventoryItem toInventoryItem(BlobItem blob) {
        String blobName = blob.getName();
        String blobUri = URLDecoder.decode(
                containerClient.getBlobClient(blobName).getBlobUrl(),
                StandardCharsets.UTF_8
            );
        String fileName = blobName.substring(blobName.lastIndexOf('/') + 1);
        return new BlobInventoryItem(
                blobUri,
                blobName,
                fileName,
                blob.getProperties().getLastModified(),
                blob.getProperties().getContentLength(),
                blob.getProperties().getETag()
        );
    }
}

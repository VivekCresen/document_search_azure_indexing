package com.cresensolutions.document_search_azure_indexing.service.Impl;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.dto.BlobInventoryItem;
import com.cresensolutions.document_search_azure_indexing.service.BlobStorageInventoryService;
import com.cresensolutions.document_search_azure_indexing.utils.CommonUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Implementation of {@link BlobStorageInventoryService} using Azure's Blob Storage SDK
 * to enumerate and filter files based on directories and supported extensions.
 */
@Service
@RequiredArgsConstructor
public class BlobStorageInventoryServiceImpl implements BlobStorageInventoryService {

    private final BlobContainerClient containerClient;
    private final AzureIndexingProperties properties;

    /**
     * Scans and inventories the configured blob container, filtering out non-supported
     * file types and files located outside the designated root storage path.
     *
     * @return a list of indexable blob descriptors
     */
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

    /**
     * Checks if the given blob is located under the configured directory prefix.
     */
    private boolean isInConfiguredDirectory(BlobItem blob, String prefix) {
        return !StringUtils.hasText(prefix) || blob.getName().startsWith(prefix);
    }

    /**
     * Filters blobs based on the allowed file extensions and excludes highlighted files.
     */
    private boolean isIndexable(BlobItem blob, List<String> supportedExtensions) {
        String lowerName = blob.getName().toLowerCase(Locale.ROOT);
        if (lowerName.contains("highlighted")) {
            return false;
        }
        return supportedExtensions.stream().anyMatch(lowerName::endsWith);
    }

    /**
     * Maps an SDK BlobItem into our internal BlobInventoryItem representation.
     */
    private BlobInventoryItem toInventoryItem(BlobItem blob) {
        String blobName = blob.getName();
        String blobUri = URLDecoder.decode(
                containerClient.getBlobClient(blobName).getBlobUrl(),
                StandardCharsets.UTF_8
            );
        String fileName = CommonUtils.filename(blobName);
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

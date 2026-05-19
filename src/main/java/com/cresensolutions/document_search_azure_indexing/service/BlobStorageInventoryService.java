package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.dto.BlobInventoryItem;
import java.util.List;

/**
 * Service interface for inventorying files from Azure Blob Storage container.
 * Performs prefix matching and filters files according to indexable extensions.
 */
public interface BlobStorageInventoryService {

    /**
     * Lists all files within the configured storage container path prefix (directory)
     * that match indexable document formats/extensions.
     *
     * @return a list of BlobInventoryItem elements describing storage properties
     */
    List<BlobInventoryItem> listIndexableBlobs();
}

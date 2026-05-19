package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.dto.BlobInventoryItem;
import java.util.List;

public interface BlobStorageInventoryService {
    List<BlobInventoryItem> listIndexableBlobs();
}

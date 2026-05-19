package com.cresensolutions.document_search_azure_indexing.service;

import java.util.Optional;

public interface FolderResolverService {
    Optional<Long> resolveFolderId(String blobUri);
}

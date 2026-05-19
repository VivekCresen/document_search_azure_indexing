package com.cresensolutions.document_search_azure_indexing.service;

import java.util.Optional;

/**
 * Service interface for resolving folder IDs from remote Azure Blob storage URIs.
 * Used to map folder structure to folder records stored in the database.
 */
public interface FolderResolverService {

    /**
     * Resolves the folder database record ID for a given blob storage URI.
     * Maps virtual storage path segments to parent folders in the relational schema.
     *
     * @param blobUri the fully qualified URI of the blob file
     * @return an Optional containing folder ID if found/resolved, or Optional.empty()
     */
    Optional<Long> resolveFolderId(String blobUri);
}

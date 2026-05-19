package com.cresensolutions.document_search_azure_indexing.service.Impl;

import com.azure.storage.blob.BlobContainerClient;
import com.cresensolutions.document_search_azure_indexing.dto.DownloadedBlob;
import com.cresensolutions.document_search_azure_indexing.service.BlobDownloadService;
import com.cresensolutions.document_search_azure_indexing.utils.CommonUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link BlobDownloadService} using official Azure SDK client
 * {@link BlobContainerClient} to download files to local temporary disk folders.
 */
@Service
@RequiredArgsConstructor
public class BlobDownloadServiceImpl implements BlobDownloadService {

    private final BlobContainerClient containerClient;

    /**
     * Downloads a remote Azure storage blob to a temporary directory on local filesystem.
     * Extracts name and details automatically if parameters are left blank.
     *
     * @param blobUri the fully qualified URI of the target blob
     * @param knownBlobName optional storage path (will extract from URI if blank)
     * @param knownFileName optional local filename (will extract from path if blank)
     * @return DownloadedBlob containing local path mapping
     */
    @Override
    public DownloadedBlob download(String blobUri, String knownBlobName, String knownFileName) {
        String blobName = CommonUtils.hasText(knownBlobName) ? knownBlobName : extractBlobName(blobUri);
        String fileName = CommonUtils.hasText(knownFileName)
                ? knownFileName
                : CommonUtils.filename(blobName);
        try {
            Path tempDir = Files.createTempDirectory("doc-index-");
            Path target = tempDir.resolve(fileName.replaceAll("[/\\\\]", "_"));
            containerClient.getBlobClient(blobName).downloadToFile(target.toString(), true);
            return new DownloadedBlob(blobUri, blobName, fileName, target);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create temp file for blob download", e);
        }
    }

    /**
     * Cleans up local temporary file and its parent temp folder.
     *
     * @param downloadedBlob downloaded blob container reference to clean up
     */
    @Override
    public void cleanup(DownloadedBlob downloadedBlob) {
        if (downloadedBlob == null || downloadedBlob.path() == null) {
            return;
        }
        try {
            Files.deleteIfExists(downloadedBlob.path());
            Path parent = downloadedBlob.path().getParent();
            if (parent != null) {
                Files.deleteIfExists(parent);
            }
        } catch (IOException ignored) {
            // Ignore failure to delete temp resources
        }
    }

    /**
     * Decodes and parses raw blob storage URI strings to extract the exact blob name path prefix.
     */
    private String extractBlobName(String blobUri) {
        String path = URI.create(blobUri.replace(" ", "%20")).getPath();
        String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8);
        String containerPrefix = "/" + containerClient.getBlobContainerName() + "/";
        if (decoded.contains(containerPrefix)) {
            return decoded.substring(decoded.indexOf(containerPrefix) + containerPrefix.length());
        }
        return decoded.replaceFirst("^/", "");
    }
}

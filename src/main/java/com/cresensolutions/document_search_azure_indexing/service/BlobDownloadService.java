package com.cresensolutions.document_search_azure_indexing.service;

import com.azure.storage.blob.BlobContainerClient;
import com.cresensolutions.document_search_azure_indexing.dto.DownloadedBlob;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class BlobDownloadService {

    private final BlobContainerClient containerClient;

    public BlobDownloadService(BlobContainerClient containerClient) {
        this.containerClient = containerClient;
    }

    public DownloadedBlob download(String blobUri, String knownBlobName, String knownFileName) {
        String blobName = StringUtils.hasText(knownBlobName) ? knownBlobName : extractBlobName(blobUri);
        String fileName = StringUtils.hasText(knownFileName)
                ? knownFileName
                : blobName.substring(blobName.lastIndexOf('/') + 1);
        try {
            Path tempDir = Files.createTempDirectory("doc-index-");
            Path target = tempDir.resolve(fileName.replaceAll("[/\\\\]", "_"));
            containerClient.getBlobClient(blobName).downloadToFile(target.toString(), true);
            return new DownloadedBlob(blobUri, blobName, fileName, target);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create temp file for blob download", e);
        }
    }

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
        }
    }

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

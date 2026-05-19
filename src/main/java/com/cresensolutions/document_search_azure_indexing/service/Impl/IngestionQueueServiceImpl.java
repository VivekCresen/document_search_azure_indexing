package com.cresensolutions.document_search_azure_indexing.service.Impl;

import com.cresensolutions.document_search_azure_indexing.domain.FileInIndex;
import com.cresensolutions.document_search_azure_indexing.domain.IndexAuditLog;
import com.cresensolutions.document_search_azure_indexing.domain.IngestionJob;
import com.cresensolutions.document_search_azure_indexing.dto.BlobInventoryItem;
import com.cresensolutions.document_search_azure_indexing.dto.BlobScanResult;
import com.cresensolutions.document_search_azure_indexing.repository.FileInIndexRepository;
import com.cresensolutions.document_search_azure_indexing.repository.IndexAuditLogRepository;
import com.cresensolutions.document_search_azure_indexing.repository.IngestionJobRepository;
import com.cresensolutions.document_search_azure_indexing.service.BlobStorageInventoryService;
import com.cresensolutions.document_search_azure_indexing.service.FolderResolverService;
import com.cresensolutions.document_search_azure_indexing.service.IngestionQueueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Implementation of {@link IngestionQueueService} managing transactional database
 * states for ingestion and deletion tasks. Uses database locking states to prevent concurrent operations.
 */
@Service
@lombok.extern.slf4j.Slf4j
@RequiredArgsConstructor
public class IngestionQueueServiceImpl implements IngestionQueueService {

    private static final Set<String> BUSY_STATUSES = Set.of("ingestion_inp", "delete_inp");

    private final BlobStorageInventoryService blobStorageInventoryService;
    private final FolderResolverService folderResolverService;
    private final IngestionJobRepository ingestionJobRepository;
    private final FileInIndexRepository fileInIndexRepository;
    private final IndexAuditLogRepository auditLogRepository;

    /**
     * Periodically scans storage container, queues new files, updates timestamps,
     * and flags missing files for deletion from search indexes.
     *
     * @return scan result counters
     */
    @Override
    @Transactional
    public BlobScanResult scanAndQueue() {
        List<BlobInventoryItem> blobs = blobStorageInventoryService.listIndexableBlobs();
        Set<String> currentBlobUris = new HashSet<>();
        List<String> queuedIngestion = new ArrayList<>();
        List<String> queuedDeletion = new ArrayList<>();
        int unchanged = 0;

        for (BlobInventoryItem blob : blobs) {
            currentBlobUris.add(blob.blobUri());
            if (queueIngestionIfNeeded(blob)) {
                queuedIngestion.add(blob.blobUri());
            } else {
                unchanged++;
            }
        }

        for (String existingUri : fileInIndexRepository.findActiveBlobUris()) {
            if (!currentBlobUris.contains(existingUri) && queueDeletion(existingUri)) {
                queuedDeletion.add(existingUri);
            }
        }

        return new BlobScanResult(
                blobs.size(),
                queuedIngestion.size(),
                queuedDeletion.size(),
                unchanged,
                queuedIngestion,
                queuedDeletion
        );
    }

    /**
     * Utility method to requeue stable files back into processing.
     *
     * @return count of requeued files
     */
    @Override
    @Transactional
    public int requeueStableFiles() {
        List<FileInIndex> stableFiles = fileInIndexRepository.findByStatus("stable");
        for (FileInIndex file : stableFiles) {
            file.setStatus("to_be_ingested");
            file.setUpdatedAt(OffsetDateTime.now());
            fileInIndexRepository.save(file);

            IngestionJob job = ingestionJobRepository.findByBlobUri(file.getBlobUri())
                    .orElseGet(() -> IngestionJob.builder().blobUri(file.getBlobUri()).build());
            job.setStatus("to_be_ingested");
            job.setFolderId(file.getFolderId());
            job.setErrorMessage(null);
            job.setUpdatedAt(OffsetDateTime.now());
            ingestionJobRepository.save(job);
            audit(job, "REQUEUE_STABLE", "to_be_ingested", "Stable file queued for re-indexing", null);
        }
        return stableFiles.size();
    }

    /**
     * Implements real-time single blob trigger queueing (e.g. from user uploads).
     *
     * @param blobUri target unique URI
     * @param blobName storage relative path
     * @param fileName local clean file name
     * @return true if queued successfully
     */
    @Override
    @Transactional
    public boolean queueSingleBlob(String blobUri, String blobName, String fileName) {
        if (blobUri == null || blobUri.isBlank()) {
            log.warn("[IndexingTrigger] Received blank blobUri — skipping");
            return false;
        }

        FileInIndex file = fileInIndexRepository.findByBlobUri(blobUri)
                .orElseGet(() -> FileInIndex.builder().blobUri(blobUri).build());

        if (BUSY_STATUSES.contains(file.getStatus())) {
            log.info("[IndexingTrigger] Blob {} is already being processed (status={}), skipping re-queue",
                    blobUri, file.getStatus());
            return false;
        }

        Long folderId = folderResolverService.resolveFolderId(blobUri).orElse(null);

        file.setFileName(fileName);
        file.setFolderId(folderId);
        file.setStatus("to_be_ingested");
        file.setUpdatedAt(OffsetDateTime.now());
        fileInIndexRepository.save(file);

        IngestionJob job = ingestionJobRepository.findByBlobUri(blobUri)
                .orElseGet(() -> IngestionJob.builder().blobUri(blobUri).build());
        job.setBlobName(blobName);
        job.setFileName(fileName);
        job.setFolderId(folderId);
        job.setStatus("to_be_ingested");
        job.setErrorMessage(null);
        job.setUpdatedAt(OffsetDateTime.now());
        ingestionJobRepository.save(job);

        audit(job, "BLOB_TRIGGER", "to_be_ingested",
                "Blob directly queued for ingestion via upload trigger",
                Map.of("file_name", fileName == null ? "" : fileName,
                       "folder_id", Objects.toString(folderId, "0")));

        log.info("[IndexingTrigger] Queued blob for ingestion: {}", blobUri);
        return true;
    }

    /**
     * Determines whether to queue an ingestion job for discovered files
     * based on last-modified timestamps and concurrency locks.
     */
    private boolean queueIngestionIfNeeded(BlobInventoryItem blob) {
        Long folderId = folderResolverService.resolveFolderId(blob.blobUri()).orElse(null);
        FileInIndex file = fileInIndexRepository.findByBlobUri(blob.blobUri())
                .orElseGet(() -> FileInIndex.builder().blobUri(blob.blobUri()).build());

        boolean isNew = file.getId() == null;
        boolean isUpdated = file.getLastModifiedBlob() == null
                || blob.lastModified() == null
                || file.getLastModifiedBlob().isBefore(blob.lastModified());

        if (!isNew && !isUpdated) {
            return false;
        }

        if (BUSY_STATUSES.contains(file.getStatus())) {
            return false;
        }

        file.setFileName(blob.fileName());
        file.setFolderId(folderId);
        file.setLastModifiedBlob(blob.lastModified());
        file.setStatus("to_be_ingested");
        file.setUpdatedAt(OffsetDateTime.now());
        fileInIndexRepository.save(file);

        IngestionJob job = ingestionJobRepository.findByBlobUri(blob.blobUri())
                .orElseGet(() -> IngestionJob.builder().blobUri(blob.blobUri()).build());
        job.setBlobName(blob.blobName());
        job.setFileName(blob.fileName());
        job.setFolderId(folderId);
        job.setBlobLastModified(blob.lastModified());
        job.setBlobEtag(blob.etag());
        job.setBlobSizeBytes(blob.sizeBytes());
        job.setStatus("to_be_ingested");
        job.setErrorMessage(null);
        job.setUpdatedAt(OffsetDateTime.now());
        ingestionJobRepository.save(job);

        audit(job, isNew ? "BLOB_DISCOVERED" : "BLOB_UPDATED", "to_be_ingested",
                isNew ? "New blob queued for ingestion" : "Updated blob queued for re-indexing",
                Map.of("file_name", blob.fileName(), "folder_id", Objects.toString(folderId, "0")));
        return true;
    }

    /**
     * Flags a missing storage blob for database and search index deletion.
     */
    private boolean queueDeletion(String blobUri) {
        FileInIndex file = fileInIndexRepository.findByBlobUri(blobUri).orElse(null);
        if (file == null || BUSY_STATUSES.contains(file.getStatus())) {
            return false;
        }

        file.setStatus("to_be_deleted");
        file.setUpdatedAt(OffsetDateTime.now());
        fileInIndexRepository.save(file);

        IngestionJob job = ingestionJobRepository.findByBlobUri(blobUri)
                .orElseGet(() -> IngestionJob.builder().blobUri(blobUri).build());
        job.setStatus("to_be_deleted");
        job.setFolderId(file.getFolderId());
        job.setUpdatedAt(OffsetDateTime.now());
        ingestionJobRepository.save(job);

        audit(job, "BLOB_MISSING", "to_be_deleted", "Blob no longer exists in storage", null);
        return true;
    }

    /**
     * Helper to persist structured audit logging events.
     */
    private void audit(IngestionJob job, String action, String status, String message, Map<String, Object> metadata) {
        auditLogRepository.save(IndexAuditLog.builder()
                .job(job)
                .blobUri(job.getBlobUri())
                .action(action)
                .status(status)
                .message(message)
                .metadata(metadata)
                .build());
    }
}

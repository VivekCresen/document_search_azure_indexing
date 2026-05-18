package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.domain.FileInIndex;
import com.cresensolutions.document_search_azure_indexing.domain.IndexAuditLog;
import com.cresensolutions.document_search_azure_indexing.domain.IngestionJob;
import com.cresensolutions.document_search_azure_indexing.dto.BlobInventoryItem;
import com.cresensolutions.document_search_azure_indexing.dto.BlobScanResult;
import com.cresensolutions.document_search_azure_indexing.repository.FileInIndexRepository;
import com.cresensolutions.document_search_azure_indexing.repository.IndexAuditLogRepository;
import com.cresensolutions.document_search_azure_indexing.repository.IngestionJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class IngestionQueueService {

    private static final Set<String> BUSY_STATUSES = Set.of("ingestion_inp", "delete_inp");

    private final BlobStorageInventoryService blobStorageInventoryService;
    private final FolderResolverService folderResolverService;
    private final IngestionJobRepository ingestionJobRepository;
    private final FileInIndexRepository fileInIndexRepository;
    private final IndexAuditLogRepository auditLogRepository;

    public IngestionQueueService(
            BlobStorageInventoryService blobStorageInventoryService,
            FolderResolverService folderResolverService,
            IngestionJobRepository ingestionJobRepository,
            FileInIndexRepository fileInIndexRepository,
            IndexAuditLogRepository auditLogRepository
    ) {
        this.blobStorageInventoryService = blobStorageInventoryService;
        this.folderResolverService = folderResolverService;
        this.ingestionJobRepository = ingestionJobRepository;
        this.fileInIndexRepository = fileInIndexRepository;
        this.auditLogRepository = auditLogRepository;
    }

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

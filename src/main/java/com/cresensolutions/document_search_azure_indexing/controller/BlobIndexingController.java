package com.cresensolutions.document_search_azure_indexing.controller;

import com.cresensolutions.document_search_azure_indexing.dto.BlobInventoryItem;
import com.cresensolutions.document_search_azure_indexing.dto.BlobScanResult;
import com.cresensolutions.document_search_azure_indexing.dto.TriggerIndexRequest;
import com.cresensolutions.document_search_azure_indexing.repository.IngestionJobRepository;
import com.cresensolutions.document_search_azure_indexing.service.BlobStorageInventoryService;
import com.cresensolutions.document_search_azure_indexing.service.IngestionQueueService;
import com.cresensolutions.document_search_azure_indexing.worker.DocumentIndexingWorkerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller providing APIs to audit storage blobs, trigger scheduled folder scans manually,
 * query queue size statistics, and queue stable documents back to ingestion states.
 */
@RestController
@RequestMapping("/api/indexing/blobs")
@RequiredArgsConstructor
@Slf4j
public class BlobIndexingController {

    private final BlobStorageInventoryService blobStorageInventoryService;
    private final IngestionQueueService ingestionQueueService;
    private final IngestionJobRepository ingestionJobRepository;
    private final DocumentIndexingWorkerService workerService;

    /**
     * Lists all indexable files found inside the Azure Blob Storage container root folders.
     *
     * @return a list of indexable blob inventory metadata items
     */
    @GetMapping
    public List<BlobInventoryItem> listBlobs() {
        return blobStorageInventoryService.listIndexableBlobs();
    }

    /**
     * Triggers a manual container partition scan and queues any discovered new/modified blobs.
     *
     * @return the scan outcomes and details of queued documents
     */
    // @PostMapping("/scan")
    // public BlobScanResult scanAndQueue() {
    //     return ingestionQueueService.scanAndQueue();
    // }

    /**
     * Resets status tags of all stable indexed files back to a queueable state.
     *
     * @return status counts of queued documents
     */
    @PostMapping("/requeue-stable")
    public Map<String, Object> requeueStableFiles() {
        return Map.of("queued", ingestionQueueService.requeueStableFiles());
    }

    /**
     * Returns total job tracking sizes matching a specific status filter.
     *
     * @param status job status name
     * @return map holding filtered queue sizes
     */
    @GetMapping("/jobs/count")
    public Map<String, Long> countJobs(@RequestParam(defaultValue = "to_be_ingested") String status) {
        return Map.of(status, ingestionJobRepository.countByStatus(status));
    }

    /**
     * Single-blob indexing trigger.
     * Called by the Spring AI service (document_search_springAi_service) immediately
     * after a successful file upload, so the document is queued for indexing without
     * waiting for the next scheduled blob scan.
     *
     * <p>Request body example:
     * <pre>
     * {
     *   "blobUri":  "https://account.blob.core.windows.net/container/folder/uuid/file.pdf",
     *   "blobName": "folder/uuid/file.pdf",
     *   "fileName": "file.pdf"
     * }
     * </pre>
     *
     * @param request the trigger payload containing blob coordinates
     * @return map with {@code queued} (boolean) and {@code blobUri}
     */
    @PostMapping("/trigger")
    public Map<String, Object> triggerSingleBlob(@RequestBody TriggerIndexRequest request) {
        boolean queued = ingestionQueueService.queueSingleBlob(
                request.blobUri(),
                request.blobName(),
                request.fileName()
        );

        if (queued) {
            // Trigger job processing asynchronously so the uploaded file is indexed instantly in the background
            CompletableFuture.runAsync(() -> {
                try {
                    // Split-second sleep to ensure the queueSingleBlob transaction is committed in the main thread
                    Thread.sleep(150);
                    log.info("[IndexingTrigger] Running real-time background processing for queued blob: {}", request.blobUri());
                    workerService.processQueuedJobs(4);
                } catch (Exception e) {
                    log.error("[IndexingTrigger] Failed to execute triggered indexing job asynchronously", e);
                }
            });
        }

        return Map.of(
                "queued", queued,
                "blobUri", request.blobUri() == null ? "" : request.blobUri()
        );
    }
}


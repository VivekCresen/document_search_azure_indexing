package com.cresensolutions.document_search_azure_indexing.controller;

import com.cresensolutions.document_search_azure_indexing.dto.BlobInventoryItem;
import com.cresensolutions.document_search_azure_indexing.dto.BlobScanResult;
import com.cresensolutions.document_search_azure_indexing.dto.TriggerIndexRequest;
import com.cresensolutions.document_search_azure_indexing.repository.IngestionJobRepository;
import com.cresensolutions.document_search_azure_indexing.service.BlobStorageInventoryService;
import com.cresensolutions.document_search_azure_indexing.service.IngestionQueueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;

/**
 * REST controller providing APIs to audit storage blobs, trigger scheduled folder scans manually,
 * query queue size statistics, and queue stable documents back to ingestion states.
 */
@RestController
@RequestMapping("/api/indexing/blobs")
@RequiredArgsConstructor
public class BlobIndexingController {

    private final BlobStorageInventoryService blobStorageInventoryService;
    private final IngestionQueueService ingestionQueueService;
    private final IngestionJobRepository ingestionJobRepository;

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
        return Map.of(
                "queued", queued,
                "blobUri", request.blobUri() == null ? "" : request.blobUri()
        );
    }
}


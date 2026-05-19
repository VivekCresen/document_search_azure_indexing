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

@RestController
@RequestMapping("/api/indexing/blobs")
@RequiredArgsConstructor
public class BlobIndexingController {

    private final BlobStorageInventoryService blobStorageInventoryService;
    private final IngestionQueueService ingestionQueueService;
    private final IngestionJobRepository ingestionJobRepository;


    @GetMapping
    public List<BlobInventoryItem> listBlobs() {
        return blobStorageInventoryService.listIndexableBlobs();
    }

    @PostMapping("/scan")
    public BlobScanResult scanAndQueue() {
        return ingestionQueueService.scanAndQueue();
    }

    @PostMapping("/requeue-stable")
    public Map<String, Object> requeueStableFiles() {
        return Map.of("queued", ingestionQueueService.requeueStableFiles());
    }

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


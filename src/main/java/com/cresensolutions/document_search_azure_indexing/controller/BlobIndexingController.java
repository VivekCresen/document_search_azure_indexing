package com.cresensolutions.document_search_azure_indexing.controller;

import com.cresensolutions.document_search_azure_indexing.dto.BlobInventoryItem;
import com.cresensolutions.document_search_azure_indexing.dto.BlobScanResult;
import com.cresensolutions.document_search_azure_indexing.repository.IngestionJobRepository;
import com.cresensolutions.document_search_azure_indexing.service.BlobStorageInventoryService;
import com.cresensolutions.document_search_azure_indexing.service.IngestionQueueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/indexing/blobs")
public class BlobIndexingController {

    private final BlobStorageInventoryService blobStorageInventoryService;
    private final IngestionQueueService ingestionQueueService;
    private final IngestionJobRepository ingestionJobRepository;

    public BlobIndexingController(
            BlobStorageInventoryService blobStorageInventoryService,
            IngestionQueueService ingestionQueueService,
            IngestionJobRepository ingestionJobRepository
    ) {
        this.blobStorageInventoryService = blobStorageInventoryService;
        this.ingestionQueueService = ingestionQueueService;
        this.ingestionJobRepository = ingestionJobRepository;
    }

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
}

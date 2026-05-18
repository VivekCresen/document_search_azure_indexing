package com.cresensolutions.document_search_azure_indexing.controller;

import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.dto.JobProcessResult;
import com.cresensolutions.document_search_azure_indexing.search.AzureSearchIndexService;
import com.cresensolutions.document_search_azure_indexing.worker.DocumentIndexingWorkerService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/indexing/jobs")
public class IndexingJobController {

    private final DocumentIndexingWorkerService workerService;
    private final AzureSearchIndexService azureSearchIndexService;
    private final AzureIndexingProperties properties;

    public IndexingJobController(
            DocumentIndexingWorkerService workerService,
            AzureSearchIndexService azureSearchIndexService,
            AzureIndexingProperties properties
    ) {
        this.workerService = workerService;
        this.azureSearchIndexService = azureSearchIndexService;
        this.properties = properties;
    }

    @PostMapping("/process")
    public JobProcessResult process(@RequestParam(required = false) Integer maxJobs) {
        int limit = maxJobs == null ? properties.settings().maxJobsPerCycle() : maxJobs;
        return workerService.processQueuedJobs(limit);
    }

    @PostMapping("/index-schema")
    public Map<String, Object> createOrUpdateIndex() {
        azureSearchIndexService.createOrUpdateIndex();
        return Map.of("index", properties.search().indexName(), "updated", true);
    }
}

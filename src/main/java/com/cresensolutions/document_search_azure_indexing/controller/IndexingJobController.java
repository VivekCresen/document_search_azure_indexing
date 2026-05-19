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

import lombok.RequiredArgsConstructor;

/**
 * REST controller providing administrative and manual endpoints to trigger
 * ingestion workers or upgrade the Cognitive Search index schema.
 */
@RestController
@RequestMapping("/api/indexing/jobs")
@RequiredArgsConstructor
public class IndexingJobController {

    private final DocumentIndexingWorkerService workerService;
    private final AzureSearchIndexService azureSearchIndexService;
    private final AzureIndexingProperties properties;

    /**
     * Triggers the ingestion worker to fetch and execute pending jobs up to a limit.
     *
     * @param maxJobs custom limit, defaults to system configuration settings
     * @return summary results of completed, skipped, or failed tasks
     */
    @PostMapping("/process")
    public JobProcessResult process(@RequestParam(required = false) Integer maxJobs) {
        int limit = maxJobs == null ? properties.settings().maxJobsPerCycle() : maxJobs;
        return workerService.processQueuedJobs(limit);
    }

    /**
     * Re-initializes or upgrades the Azure Vector Index schema based on code definitions.
     *
     * @return status mapping containing the affected index name
     */
    @PostMapping("/index-schema")
    public Map<String, Object> createOrUpdateIndex() {
        azureSearchIndexService.createOrUpdateIndex();
        return Map.of("index", properties.search().indexName(), "updated", true);
    }
}

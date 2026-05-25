package com.cresensolutions.document_search_azure_indexing.controller;

import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.dto.JobProcessResult;
import com.cresensolutions.document_search_azure_indexing.search.AzureSearchIndexService;
import com.cresensolutions.document_search_azure_indexing.worker.DocumentIndexingWorkerService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    // @PostMapping("/process")
    // public JobProcessResult process(@RequestParam(required = false) Integer maxJobs) {
    //     int limit = maxJobs == null ? properties.settings().maxJobsPerCycle() : maxJobs;
    //     return workerService.processQueuedJobs(limit);
    // }

    /**
     * Re-initializes or upgrades the Azure Vector Index schema based on code definitions.
     *
     * @return status mapping containing the affected index name
     */
    @PostMapping("/index-schema")
    public Map<String, Object> createOrUpdateIndex() {
        azureSearchIndexService.createOrUpdateIndex();
        return Map.of(
                "index", properties.search().indexName(),
                "updated", true,
                "message", "Index schema is available"
        );
    }

    /**
     * Deletes the configured Azure Vector Index.
     * 
     * @return status mapping containing the affected index name
     */
    @DeleteMapping("/index-schema")
    public Map<String, Object> deleteConfiguredIndex() {
        azureSearchIndexService.deleteIndex(properties.search().indexName());
        return Map.of("index", properties.search().indexName(), "deleted", true);
    }

    /**
     * Lists all available indexes in Azure Cognitive Search.
     * 
     * @return mapping containing the list of indexes
     */
    @GetMapping("/indexes")
    public Map<String, Object> listIndexes() {
        return Map.of("indexes", azureSearchIndexService.listIndexes());
    }

    /**
     * Deletes a specific index by name.
     * 
     * @param indexName the name of the index to delete
     * @return status mapping containing the deleted index name
     */
    @DeleteMapping("/indexes/{indexName}")
    public Map<String, Object> deleteIndex(@PathVariable String indexName) {
        azureSearchIndexService.deleteIndex(indexName);
        return Map.of("index", indexName, "deleted", true);
    }
    

    @DeleteMapping("/documents")
    public Map<String, Object> clearIndexDocuments() {
        int deleted = azureSearchIndexService.clearConfiguredIndex();
        return Map.of("index", properties.search().indexName(), "deletedDocuments", deleted);
    }
}

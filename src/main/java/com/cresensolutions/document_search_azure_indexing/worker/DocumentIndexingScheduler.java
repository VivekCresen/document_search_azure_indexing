package com.cresensolutions.document_search_azure_indexing.worker;

import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.dto.JobProcessResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "azure.indexing.settings", name = "job-processing-enabled", havingValue = "true")
public class DocumentIndexingScheduler {

    private final DocumentIndexingWorkerService workerService;
    private final AzureIndexingProperties properties;

    public DocumentIndexingScheduler(DocumentIndexingWorkerService workerService, AzureIndexingProperties properties) {
        this.workerService = workerService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${azure.indexing.settings.job-processing-fixed-delay-ms:120000}")
    public void processQueuedJobs() {
        JobProcessResult result = workerService.processQueuedJobs(properties.settings().maxJobsPerCycle());
        log.info("Indexing cycle complete: processed={}, succeeded={}, failed={}",
                result.processed(), result.succeeded(), result.failed());
    }
}

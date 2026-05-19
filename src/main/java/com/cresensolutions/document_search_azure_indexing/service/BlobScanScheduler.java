package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.dto.BlobScanResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "azure.indexing.settings", name = "blob-scan-enabled", havingValue = "true")
public class BlobScanScheduler {

    private final IngestionQueueService ingestionQueueService;


    @Scheduled(fixedDelayString = "${azure.indexing.settings.blob-scan-fixed-delay-ms:60000}")
    public void scanAndQueue() {
        try {
            BlobScanResult result = ingestionQueueService.scanAndQueue();
            log.info(
                    "Blob scan complete: scanned={}, queuedIngestion={}, queuedDeletion={}, unchanged={}",
                    result.scanned(),
                    result.queuedForIngestion(),
                    result.queuedForDeletion(),
                    result.unchanged()
            );
        } catch (Exception e) {
            log.warn("Blob scan failed", e);
        }
    }
}

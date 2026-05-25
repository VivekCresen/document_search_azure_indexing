package com.cresensolutions.document_search_azure_indexing.worker;

import com.cresensolutions.document_search_azure_indexing.commons.Constants;
import com.cresensolutions.document_search_azure_indexing.domain.FileInIndex;
import com.cresensolutions.document_search_azure_indexing.domain.IngestionJob;
import com.cresensolutions.document_search_azure_indexing.repository.FileInIndexRepository;
import com.cresensolutions.document_search_azure_indexing.repository.IngestionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Startup event listener that scans for jobs that were left stuck in progress 
 * ('ingestion_inp' or 'delete_inp') due to unexpected JVM shutdown, crash, or 
 * development restart, and recovers them back to queueable states.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JobRecoveryHandler {

    private final IngestionJobRepository jobRepository;
    private final FileInIndexRepository fileRepository;

    /**
     * Resets any stuck jobs on application startup when the application context is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverStuckJobs() {
        log.info("[Recovery] Checking for stuck indexing or deletion jobs from a previous run...");

        List<IngestionJob> stuckIngestions = jobRepository.findByStatus(Constants.JOB_STATUS_INGESTION_INP);
        if (!stuckIngestions.isEmpty()) {
            log.info("[Recovery] Found {} ingestion jobs stuck in progress. Resetting to '{}'...", 
                    stuckIngestions.size(), Constants.JOB_STATUS_TO_BE_INGESTED);
            stuckIngestions.forEach(job -> {
                job.setStatus(Constants.JOB_STATUS_TO_BE_INGESTED);
                job.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                jobRepository.save(job);

                fileRepository.findByBlobUri(job.getBlobUri()).ifPresent(file -> {
                    file.setStatus(Constants.JOB_STATUS_TO_BE_INGESTED);
                    file.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                    fileRepository.save(file);
                });
            });
        }

        List<IngestionJob> stuckDeletions = jobRepository.findByStatus(Constants.JOB_STATUS_DELETE_INP);
        if (!stuckDeletions.isEmpty()) {
            log.info("[Recovery] Found {} deletion jobs stuck in progress. Resetting to '{}'...", 
                    stuckDeletions.size(), Constants.JOB_STATUS_TO_BE_DELETED);
            stuckDeletions.forEach(job -> {
                job.setStatus(Constants.JOB_STATUS_TO_BE_DELETED);
                job.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                jobRepository.save(job);

                fileRepository.findByBlobUri(job.getBlobUri()).ifPresent(file -> {
                    file.setStatus(Constants.JOB_STATUS_TO_BE_DELETED);
                    file.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                    fileRepository.save(file);
                });
            });
        }
        
        log.info("[Recovery] Stuck job check complete.");
    }
}

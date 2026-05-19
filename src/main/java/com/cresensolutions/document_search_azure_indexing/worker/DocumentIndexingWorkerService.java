package com.cresensolutions.document_search_azure_indexing.worker;

import com.cresensolutions.document_search_azure_indexing.domain.IngestionJob;
import com.cresensolutions.document_search_azure_indexing.dto.JobProcessResult;
import java.util.List;

public interface DocumentIndexingWorkerService {
    JobProcessResult processQueuedJobs(int maxJobs);
    List<IngestionJob> lockJobs(String status, int maxJobs);
    void completeJob(IngestionJob job, int chunkCount);
    void failJob(IngestionJob job, Exception e);
}

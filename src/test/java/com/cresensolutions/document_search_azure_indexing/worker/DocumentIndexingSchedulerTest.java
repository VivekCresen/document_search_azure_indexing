package com.cresensolutions.document_search_azure_indexing.worker;

import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.dto.JobProcessResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentIndexingSchedulerTest {

    @Mock
    private DocumentIndexingWorkerService workerService;

    @Mock
    private AzureIndexingProperties properties;

    @Mock
    private AzureIndexingProperties.Settings settings;

    @InjectMocks
    private DocumentIndexingScheduler scheduler;

    @Test
    void testProcessQueuedJobs() {
        when(properties.settings()).thenReturn(settings);
        when(settings.maxJobsPerCycle()).thenReturn(5);

        JobProcessResult expectedResult = new JobProcessResult(5, 4, 1);
        when(workerService.processQueuedJobs(5)).thenReturn(expectedResult);

        scheduler.processQueuedJobs();

        verify(workerService, times(1)).processQueuedJobs(5);
    }
}

package com.cresensolutions.document_search_azure_indexing.controller;

import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.dto.JobProcessResult;
import com.cresensolutions.document_search_azure_indexing.search.AzureSearchIndexService;
import com.cresensolutions.document_search_azure_indexing.worker.DocumentIndexingWorkerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndexingJobControllerTest {

    @Mock
    private DocumentIndexingWorkerService workerService;

    @Mock
    private AzureSearchIndexService azureSearchIndexService;

    @Mock
    private AzureIndexingProperties properties;

    @Mock
    private AzureIndexingProperties.Settings settings;

    @Mock
    private AzureIndexingProperties.Search search;

    @InjectMocks
    private IndexingJobController controller;

    @Test
    void testProcessWithProvidedLimit() {
        JobProcessResult expectedResult = new JobProcessResult(5, 4, 1);
        when(workerService.processQueuedJobs(10)).thenReturn(expectedResult);

        JobProcessResult result = controller.process(10);

        assertEquals(expectedResult, result);
        verify(workerService).processQueuedJobs(10);
    }

    @Test
    void testProcessWithNullLimit() {
        JobProcessResult expectedResult = new JobProcessResult(2, 2, 0);
        when(properties.settings()).thenReturn(settings);
        when(settings.maxJobsPerCycle()).thenReturn(4);
        when(workerService.processQueuedJobs(4)).thenReturn(expectedResult);

        JobProcessResult result = controller.process(null);

        assertEquals(expectedResult, result);
        verify(workerService).processQueuedJobs(4);
    }

    @Test
    void testCreateOrUpdateIndex() {
        when(properties.search()).thenReturn(search);
        when(search.indexName()).thenReturn("document-index");

        Map<String, Object> result = controller.createOrUpdateIndex();

        assertEquals("document-index", result.get("index"));
        assertEquals(true, result.get("updated"));
        verify(azureSearchIndexService, times(1)).createOrUpdateIndex();
    }
}

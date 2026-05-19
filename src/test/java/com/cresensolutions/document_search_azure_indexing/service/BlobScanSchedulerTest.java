package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.dto.BlobScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlobScanSchedulerTest {

    @Mock
    private IngestionQueueService ingestionQueueService;

    @InjectMocks
    private BlobScanScheduler scheduler;

    @Test
    void testScanAndQueueSuccess() {
        BlobScanResult result = new BlobScanResult(5, 2, 1, 2, List.of("uri1"), List.of("uri2"));
        when(ingestionQueueService.scanAndQueue()).thenReturn(result);

        scheduler.scanAndQueue();

        verify(ingestionQueueService, times(1)).scanAndQueue();
    }

    @Test
    void testScanAndQueueExceptionHandled() {
        when(ingestionQueueService.scanAndQueue()).thenThrow(new RuntimeException("Storage failure"));

        scheduler.scanAndQueue();

        verify(ingestionQueueService, times(1)).scanAndQueue();
    }
}

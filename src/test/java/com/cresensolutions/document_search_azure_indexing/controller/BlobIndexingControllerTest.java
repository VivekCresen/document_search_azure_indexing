package com.cresensolutions.document_search_azure_indexing.controller;

import com.cresensolutions.document_search_azure_indexing.dto.BlobInventoryItem;
import com.cresensolutions.document_search_azure_indexing.dto.BlobScanResult;
import com.cresensolutions.document_search_azure_indexing.dto.TriggerIndexRequest;
import com.cresensolutions.document_search_azure_indexing.repository.IngestionJobRepository;
import com.cresensolutions.document_search_azure_indexing.service.BlobStorageInventoryService;
import com.cresensolutions.document_search_azure_indexing.service.IngestionQueueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlobIndexingControllerTest {

    @Mock
    private BlobStorageInventoryService blobStorageInventoryService;

    @Mock
    private IngestionQueueService ingestionQueueService;

    @Mock
    private IngestionJobRepository ingestionJobRepository;

    @InjectMocks
    private BlobIndexingController controller;

    @Test
    void testListBlobs() {
        BlobInventoryItem item = new BlobInventoryItem("uri", "name", "file", OffsetDateTime.now(), 10L, "etag");
        when(blobStorageInventoryService.listIndexableBlobs()).thenReturn(List.of(item));

        List<BlobInventoryItem> result = controller.listBlobs();
        assertEquals(1, result.size());
        assertEquals("uri", result.get(0).blobUri());
    }

    @Test
    void testScanAndQueue() {
        BlobScanResult scanResult = new BlobScanResult(10, 5, 2, 3, List.of("uri1"), List.of("uri2"));
        when(ingestionQueueService.scanAndQueue()).thenReturn(scanResult);

        BlobScanResult result = controller.scanAndQueue();
        assertEquals(10, result.scanned());
        assertEquals(5, result.queuedForIngestion());
    }

    @Test
    void testRequeueStableFiles() {
        when(ingestionQueueService.requeueStableFiles()).thenReturn(7);

        Map<String, Object> result = controller.requeueStableFiles();
        assertEquals(7, result.get("queued"));
    }

    @Test
    void testCountJobs() {
        when(ingestionJobRepository.countByStatus("to_be_ingested")).thenReturn(15L);

        Map<String, Long> result = controller.countJobs("to_be_ingested");
        assertEquals(15L, result.get("to_be_ingested"));
    }

    @Test
    void testTriggerSingleBlob() {
        TriggerIndexRequest request = new TriggerIndexRequest("https://my-blob", "path/my-blob", "my-blob");
        when(ingestionQueueService.queueSingleBlob("https://my-blob", "path/my-blob", "my-blob")).thenReturn(true);

        Map<String, Object> result = controller.triggerSingleBlob(request);
        assertEquals(true, result.get("queued"));
        assertEquals("https://my-blob", result.get("blobUri"));
    }

    @Test
    void testTriggerSingleBlobWithNullUri() {
        TriggerIndexRequest request = new TriggerIndexRequest(null, "path/my-blob", "my-blob");
        when(ingestionQueueService.queueSingleBlob(null, "path/my-blob", "my-blob")).thenReturn(false);

        Map<String, Object> result = controller.triggerSingleBlob(request);
        assertEquals(false, result.get("queued"));
        assertEquals("", result.get("blobUri"));
    }
}

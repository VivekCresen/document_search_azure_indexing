package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.service.Impl.IngestionQueueServiceImpl;
import com.cresensolutions.document_search_azure_indexing.domain.FileInIndex;
import com.cresensolutions.document_search_azure_indexing.domain.IndexAuditLog;
import com.cresensolutions.document_search_azure_indexing.domain.IngestionJob;
import com.cresensolutions.document_search_azure_indexing.dto.BlobInventoryItem;
import com.cresensolutions.document_search_azure_indexing.dto.BlobScanResult;
import com.cresensolutions.document_search_azure_indexing.repository.FileInIndexRepository;
import com.cresensolutions.document_search_azure_indexing.repository.IndexAuditLogRepository;
import com.cresensolutions.document_search_azure_indexing.repository.IngestionJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionQueueServiceTest {

    @Mock
    private BlobStorageInventoryService blobStorageInventoryService;

    @Mock
    private FolderResolverService folderResolverService;

    @Mock
    private IngestionJobRepository ingestionJobRepository;

    @Mock
    private FileInIndexRepository fileInIndexRepository;

    @Mock
    private IndexAuditLogRepository auditLogRepository;

    @InjectMocks
    private IngestionQueueServiceImpl service;

    @Test
    void testScanAndQueueWithNewUpdatedAndDeletedBlobs() {
        OffsetDateTime now = OffsetDateTime.now();

        // Inventory returns 3 items:
        // 1. New blob
        BlobInventoryItem item1 = new BlobInventoryItem("uri1", "name1", "file1.pdf", now, 100L, "etag1");
        // 2. Updated blob
        BlobInventoryItem item2 = new BlobInventoryItem("uri2", "name2", "file2.pdf", now, 200L, "etag2");
        // 3. Unchanged blob
        BlobInventoryItem item3 = new BlobInventoryItem("uri3", "name3", "file3.pdf", now, 300L, "etag3");

        when(blobStorageInventoryService.listIndexableBlobs()).thenReturn(List.of(item1, item2, item3));

        // Mock folder resolution
        when(folderResolverService.resolveFolderId("uri1")).thenReturn(Optional.of(10L));
        when(folderResolverService.resolveFolderId("uri2")).thenReturn(Optional.of(20L));

        // Mock repository calls for ingestion checks
        // item1 is new
        when(fileInIndexRepository.findByBlobUri("uri1")).thenReturn(Optional.empty());
        // item2 is updated (last modified is older in db)
        FileInIndex file2InDb = FileInIndex.builder().id(2L).blobUri("uri2").lastModifiedBlob(now.minusDays(1)).status("stable").build();
        when(fileInIndexRepository.findByBlobUri("uri2")).thenReturn(Optional.of(file2InDb));
        // item3 is unchanged (last modified is same or newer in db)
        FileInIndex file3InDb = FileInIndex.builder().id(3L).blobUri("uri3").lastModifiedBlob(now).status("stable").build();
        when(fileInIndexRepository.findByBlobUri("uri3")).thenReturn(Optional.of(file3InDb));

        // Stub findByBlobUri for IngestionJob saves
        when(ingestionJobRepository.findByBlobUri("uri1")).thenReturn(Optional.empty());
        when(ingestionJobRepository.findByBlobUri("uri2")).thenReturn(Optional.empty());

        // Deletion check: Active Uris in DB has uri4 (which is deleted) and uri1 (which is active)
        when(fileInIndexRepository.findActiveBlobUris()).thenReturn(List.of("uri1", "uri4"));
        // file4 exists in DB and is not busy
        FileInIndex file4InDb = FileInIndex.builder().id(4L).blobUri("uri4").status("stable").folderId(40L).build();
        when(fileInIndexRepository.findByBlobUri("uri4")).thenReturn(Optional.of(file4InDb));
        when(ingestionJobRepository.findByBlobUri("uri4")).thenReturn(Optional.empty());

        BlobScanResult result = service.scanAndQueue();

        assertEquals(3, result.scanned());
        assertEquals(2, result.queuedForIngestion());
        assertEquals(1, result.queuedForDeletion());
        assertEquals(1, result.unchanged());
        assertEquals(List.of("uri1", "uri2"), result.ingestionBlobUris());
        assertEquals(List.of("uri4"), result.deletionBlobUris());

        // Verify saves occurred
        verify(fileInIndexRepository, times(3)).save(any(FileInIndex.class));
        verify(ingestionJobRepository, times(3)).save(any(IngestionJob.class));
        verify(auditLogRepository, times(3)).save(any(IndexAuditLog.class));
    }

    @Test
    void testScanAndQueueBusyStateSkips() {
        OffsetDateTime now = OffsetDateTime.now();

        BlobInventoryItem item1 = new BlobInventoryItem("uri1", "name1", "file1.pdf", now, 100L, "etag1");
        when(blobStorageInventoryService.listIndexableBlobs()).thenReturn(List.of(item1));

        // item1 exists in DB and status is busy (ingestion_inp)
        FileInIndex file1InDb = FileInIndex.builder().id(1L).blobUri("uri1").lastModifiedBlob(now.minusDays(1)).status("ingestion_inp").build();
        when(fileInIndexRepository.findByBlobUri("uri1")).thenReturn(Optional.of(file1InDb));

        // Deletion check: active URIs contains uri2
        when(fileInIndexRepository.findActiveBlobUris()).thenReturn(List.of("uri2"));
        // uri2 in DB status is busy (delete_inp)
        FileInIndex file2InDb = FileInIndex.builder().id(2L).blobUri("uri2").status("delete_inp").build();
        when(fileInIndexRepository.findByBlobUri("uri2")).thenReturn(Optional.of(file2InDb));

        BlobScanResult result = service.scanAndQueue();

        assertEquals(1, result.scanned());
        assertEquals(0, result.queuedForIngestion());
        assertEquals(0, result.queuedForDeletion());
        assertEquals(1, result.unchanged());
    }

    @Test
    void testRequeueStableFiles() {
        FileInIndex file1 = FileInIndex.builder().id(1L).blobUri("uri1").folderId(10L).status("stable").build();
        FileInIndex file2 = FileInIndex.builder().id(2L).blobUri("uri2").folderId(20L).status("stable").build();

        when(fileInIndexRepository.findByStatus("stable")).thenReturn(List.of(file1, file2));
        when(ingestionJobRepository.findByBlobUri("uri1")).thenReturn(Optional.empty());
        when(ingestionJobRepository.findByBlobUri("uri2")).thenReturn(Optional.of(IngestionJob.builder().blobUri("uri2").build()));

        int count = service.requeueStableFiles();

        assertEquals(2, count);
        verify(fileInIndexRepository, times(2)).save(any(FileInIndex.class));
        verify(ingestionJobRepository, times(2)).save(any(IngestionJob.class));
        verify(auditLogRepository, times(2)).save(any(IndexAuditLog.class));
    }

    @Test
    void testQueueSingleBlobBlankUri() {
        assertFalse(service.queueSingleBlob(null, "name", "file"));
        assertFalse(service.queueSingleBlob("  ", "name", "file"));
    }

    @Test
    void testQueueSingleBlobBusyState() {
        FileInIndex file = FileInIndex.builder().blobUri("uri").status("delete_inp").build();
        when(fileInIndexRepository.findByBlobUri("uri")).thenReturn(Optional.of(file));

        boolean queued = service.queueSingleBlob("uri", "name", "file");

        assertFalse(queued);
        verify(fileInIndexRepository, never()).save(any());
    }

    @Test
    void testQueueSingleBlobSuccessful() {
        when(fileInIndexRepository.findByBlobUri("uri")).thenReturn(Optional.empty());
        when(folderResolverService.resolveFolderId("uri")).thenReturn(Optional.of(99L));
        when(ingestionJobRepository.findByBlobUri("uri")).thenReturn(Optional.empty());

        boolean queued = service.queueSingleBlob("uri", "name", "file.pdf");

        assertTrue(queued);
        verify(fileInIndexRepository).save(any(FileInIndex.class));
        verify(ingestionJobRepository).save(any(IngestionJob.class));
        verify(auditLogRepository).save(any(IndexAuditLog.class));
    }
}

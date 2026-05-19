package com.cresensolutions.document_search_azure_indexing.worker;

import com.cresensolutions.document_search_azure_indexing.worker.Impl.DocumentIndexingWorkerServiceImpl;
import com.cresensolutions.document_search_azure_indexing.domain.FileInIndex;
import com.cresensolutions.document_search_azure_indexing.domain.IndexAuditLog;
import com.cresensolutions.document_search_azure_indexing.domain.IndexedChunk;
import com.cresensolutions.document_search_azure_indexing.domain.IngestionJob;
import com.cresensolutions.document_search_azure_indexing.dto.DocumentChunk;
import com.cresensolutions.document_search_azure_indexing.dto.DownloadedBlob;
import com.cresensolutions.document_search_azure_indexing.dto.EnrichmentResult;
import com.cresensolutions.document_search_azure_indexing.dto.JobProcessResult;
import com.cresensolutions.document_search_azure_indexing.dto.ParsedDocument;
import com.cresensolutions.document_search_azure_indexing.parser.DocumentParserService;
import com.cresensolutions.document_search_azure_indexing.repository.FileInIndexRepository;
import com.cresensolutions.document_search_azure_indexing.repository.IndexAuditLogRepository;
import com.cresensolutions.document_search_azure_indexing.repository.IndexedChunkRepository;
import com.cresensolutions.document_search_azure_indexing.repository.IngestionJobRepository;
import com.cresensolutions.document_search_azure_indexing.search.AzureSearchIndexService;
import com.cresensolutions.document_search_azure_indexing.service.BlobDownloadService;
import com.cresensolutions.document_search_azure_indexing.service.DocumentChunkingService;
import com.cresensolutions.document_search_azure_indexing.service.MetadataEnrichmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentIndexingWorkerServiceTest {

    @Mock
    private IngestionJobRepository ingestionJobRepository;

    @Mock
    private FileInIndexRepository fileInIndexRepository;

    @Mock
    private IndexedChunkRepository indexedChunkRepository;

    @Mock
    private IndexAuditLogRepository auditLogRepository;

    @Mock
    private BlobDownloadService blobDownloadService;

    @Mock
    private DocumentParserService documentParserService;

    @Mock
    private DocumentChunkingService documentChunkingService;

    @Mock
    private MetadataEnrichmentService metadataEnrichmentService;

    @Mock
    private AzureSearchIndexService azureSearchIndexService;

    @Mock
    private ObjectProvider<EmbeddingModel> embeddingModelProvider;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DocumentIndexingWorkerServiceImpl service;

    @Test
    void testLockJobsWhenLimitZeroOrNegative() {
        List<IngestionJob> locked = service.lockJobs("to_be_ingested", 0);
        assertTrue(locked.isEmpty());
    }

    @Test
    void testProcessQueuedJobsSuccess() throws Exception {
        // Mocking two jobs: 1 Ingestion, 1 Deletion
        IngestionJob ingestionJob = IngestionJob.builder()
                .id(1L)
                .blobUri("http://my-ingest-blob")
                .blobName("ingest-blob")
                .fileName("file.pdf")
                .status("to_be_ingested")
                .attempts(0)
                .maxAttempts(3)
                .build();

        IngestionJob deletionJob = IngestionJob.builder()
                .id(2L)
                .blobUri("http://my-delete-blob")
                .status("to_be_deleted")
                .attempts(0)
                .maxAttempts(3)
                .build();

        when(ingestionJobRepository.findByStatusOrderByCreatedAtAsc(eq("to_be_ingested"), any(Pageable.class)))
                .thenReturn(List.of(ingestionJob));
        when(ingestionJobRepository.findByStatusOrderByCreatedAtAsc(eq("to_be_deleted"), any(Pageable.class)))
                .thenReturn(List.of(deletionJob));

        when(ingestionJobRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Stub Ingestion calls
        DownloadedBlob downloadedBlob = new DownloadedBlob("http://my-ingest-blob", "ingest-blob", "file.pdf", Path.of("temp"));
        when(blobDownloadService.download("http://my-ingest-blob", "ingest-blob", "file.pdf")).thenReturn(downloadedBlob);

        ParsedDocument parsed = new ParsedDocument("text", "title", Map.of(), List.of());
        when(documentParserService.parse(downloadedBlob)).thenReturn(parsed);

        DocumentChunk chunk = new DocumentChunk(0, "content", "title", 1, Map.of(), List.of());
        when(documentChunkingService.chunk(parsed)).thenReturn(List.of(chunk));

        when(embeddingModelProvider.getIfAvailable()).thenReturn(embeddingModel);
        when(embeddingModel.embed("content")).thenReturn(new float[]{0.1f, 0.2f});

        EnrichmentResult enrichmentResult = new EnrichmentResult(List.of("topic"), List.of("query"), List.of("signal"));
        when(metadataEnrichmentService.enrich("content")).thenReturn(enrichmentResult);

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // Stub Deletion calls
        IndexedChunk chunkToDelete = IndexedChunk.builder().searchDocumentId("doc-id-123").build();
        when(indexedChunkRepository.findByBlobUri("http://my-delete-blob")).thenReturn(List.of(chunkToDelete));

        FileInIndex fileToDelete = FileInIndex.builder().blobUri("http://my-delete-blob").build();
        when(fileInIndexRepository.findByBlobUri("http://my-delete-blob")).thenReturn(Optional.of(fileToDelete));

        // Call the service
        JobProcessResult result = service.processQueuedJobs(2);

        assertEquals(2, result.processed());
        assertEquals(2, result.succeeded());
        assertEquals(0, result.failed());

        verify(azureSearchIndexService).uploadDocuments(anyList());
        verify(azureSearchIndexService).deleteDocumentsByIds(List.of("doc-id-123"));
        verify(fileInIndexRepository).delete(fileToDelete);
        verify(blobDownloadService).cleanup(downloadedBlob);
    }

    @Test
    void testProcessIngestionThrowsNoEmbeddingModel() throws Exception {
        IngestionJob ingestionJob = IngestionJob.builder()
                .id(1L)
                .blobUri("http://my-ingest-blob")
                .blobName("ingest-blob")
                .fileName("file.pdf")
                .status("to_be_ingested")
                .attempts(0)
                .maxAttempts(3)
                .build();

        when(ingestionJobRepository.findByStatusOrderByCreatedAtAsc(eq("to_be_ingested"), any(Pageable.class)))
                .thenReturn(List.of(ingestionJob));
        when(ingestionJobRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        DownloadedBlob downloadedBlob = new DownloadedBlob("http://my-ingest-blob", "ingest-blob", "file.pdf", Path.of("temp"));
        when(blobDownloadService.download("http://my-ingest-blob", "ingest-blob", "file.pdf")).thenReturn(downloadedBlob);

        ParsedDocument parsed = new ParsedDocument("text", "title", Map.of(), List.of());
        when(documentParserService.parse(downloadedBlob)).thenReturn(parsed);

        DocumentChunk chunk = new DocumentChunk(0, "content", "title", 1, Map.of(), List.of());
        when(documentChunkingService.chunk(parsed)).thenReturn(List.of(chunk));

        // Return null for embedding model provider to trigger error
        when(embeddingModelProvider.getIfAvailable()).thenReturn(null);

        JobProcessResult result = service.processQueuedJobs(1);

        assertEquals(1, result.processed());
        assertEquals(1, result.failed());

        // Verify it was transitioned back to to_be_ingested or failed (attempts was 0, locked incremented it to 1, still less than 3 maxAttempts)
        assertEquals("to_be_ingested", ingestionJob.getStatus());
        assertNotNull(ingestionJob.getErrorMessage());
    }

    @Test
    void testProcessIngestionReachesMaxAttempts() throws Exception {
        IngestionJob ingestionJob = IngestionJob.builder()
                .id(1L)
                .blobUri("http://my-ingest-blob")
                .blobName("ingest-blob")
                .fileName("file.pdf")
                .status("to_be_ingested")
                .attempts(2) // locked increment to 3
                .maxAttempts(3)
                .build();

        when(ingestionJobRepository.findByStatusOrderByCreatedAtAsc(eq("to_be_ingested"), any(Pageable.class)))
                .thenReturn(List.of(ingestionJob));
        when(ingestionJobRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        DownloadedBlob downloadedBlob = new DownloadedBlob("http://my-ingest-blob", "ingest-blob", "file.pdf", Path.of("temp"));
        when(blobDownloadService.download("http://my-ingest-blob", "ingest-blob", "file.pdf")).thenReturn(downloadedBlob);

        ParsedDocument parsed = new ParsedDocument("text", "title", Map.of(), List.of());
        when(documentParserService.parse(downloadedBlob)).thenReturn(parsed);

        // No chunks produced exception path
        when(documentChunkingService.chunk(parsed)).thenReturn(List.of());

        JobProcessResult result = service.processQueuedJobs(1);

        assertEquals(1, result.processed());
        assertEquals(1, result.failed());

        // Since attempts (3) is now >= maxAttempts (3), status changes to failed
        assertEquals("failed", ingestionJob.getStatus());
    }

    @Test
    void testHostnameExceptionFallback() {
        IngestionJob job = IngestionJob.builder().id(1L).attempts(0).build();
        when(ingestionJobRepository.findByStatusOrderByCreatedAtAsc(eq("to_be_ingested"), any(Pageable.class)))
                .thenReturn(List.of(job));
        when(ingestionJobRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<InetAddress> mockedInet = mockStatic(InetAddress.class)) {
            mockedInet.when(InetAddress::getLocalHost).thenThrow(new UnknownHostException("Network Error"));

            List<IngestionJob> locked = service.lockJobs("to_be_ingested", 1);
            assertFalse(locked.isEmpty());
            assertEquals("document-search-azure-indexing", locked.get(0).getLockedBy());
        }
    }
}

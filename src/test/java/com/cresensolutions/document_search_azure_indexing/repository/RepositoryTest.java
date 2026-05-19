package com.cresensolutions.document_search_azure_indexing.repository;

import com.cresensolutions.document_search_azure_indexing.domain.FileInIndex;
import com.cresensolutions.document_search_azure_indexing.domain.IndexAuditLog;
import com.cresensolutions.document_search_azure_indexing.domain.IndexedChunk;
import com.cresensolutions.document_search_azure_indexing.domain.IngestionJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class RepositoryTest {

    @Autowired
    private FileInIndexRepository fileInIndexRepository;

    @Autowired
    private IngestionJobRepository ingestionJobRepository;

    @Autowired
    private IndexedChunkRepository indexedChunkRepository;

    @Autowired
    private IndexAuditLogRepository indexAuditLogRepository;

    @Test
    void testFileInIndexRepository() {
        FileInIndex file1 = FileInIndex.builder()
                .blobUri("http://my-blob-1")
                .fileName("file1.pdf")
                .status("stable")
                .updatedAt(OffsetDateTime.now())
                .build();

        FileInIndex file2 = FileInIndex.builder()
                .blobUri("http://my-blob-2")
                .fileName("file2.pdf")
                .status("to_be_deleted")
                .updatedAt(OffsetDateTime.now())
                .build();

        fileInIndexRepository.saveAll(List.of(file1, file2));

        Optional<FileInIndex> found = fileInIndexRepository.findByBlobUri("http://my-blob-1");
        assertTrue(found.isPresent());
        assertEquals("file1.pdf", found.get().getFileName());

        List<FileInIndex> stableFiles = fileInIndexRepository.findByStatus("stable");
        assertEquals(1, stableFiles.size());

        List<String> activeUris = fileInIndexRepository.findActiveBlobUris();
        assertEquals(1, activeUris.size());
        assertEquals("http://my-blob-1", activeUris.get(0));
    }

    @Test
    void testIngestionJobRepository() {
        IngestionJob job1 = IngestionJob.builder()
                .blobUri("http://job-blob-1")
                .status("to_be_ingested")
                .createdAt(OffsetDateTime.now().minusMinutes(5))
                .build();

        IngestionJob job2 = IngestionJob.builder()
                .blobUri("http://job-blob-2")
                .status("to_be_ingested")
                .createdAt(OffsetDateTime.now())
                .build();

        ingestionJobRepository.saveAll(List.of(job1, job2));

        Optional<IngestionJob> found = ingestionJobRepository.findByBlobUri("http://job-blob-1");
        assertTrue(found.isPresent());

        List<IngestionJob> top100 = ingestionJobRepository.findTop100ByStatusOrderByCreatedAtAsc("to_be_ingested");
        assertEquals(2, top100.size());
        assertEquals("http://job-blob-1", top100.get(0).getBlobUri());

        List<IngestionJob> paged = ingestionJobRepository.findByStatusOrderByCreatedAtAsc("to_be_ingested", PageRequest.of(0, 1));
        assertEquals(1, paged.size());
        assertEquals("http://job-blob-1", paged.get(0).getBlobUri());

        long count = ingestionJobRepository.countByStatus("to_be_ingested");
        assertEquals(2L, count);
    }

    @Test
    void testIndexedChunkRepository() {
        IndexedChunk chunk1 = IndexedChunk.builder()
                .blobUri("http://chunk-blob")
                .searchDocumentId("doc-1")
                .chunkNumber(1)
                .build();

        IndexedChunk chunk2 = IndexedChunk.builder()
                .blobUri("http://chunk-blob")
                .searchDocumentId("doc-2")
                .chunkNumber(2)
                .build();

        indexedChunkRepository.saveAll(List.of(chunk1, chunk2));

        List<IndexedChunk> chunks = indexedChunkRepository.findByBlobUri("http://chunk-blob");
        assertEquals(2, chunks.size());

        indexedChunkRepository.deleteByBlobUri("http://chunk-blob");
        assertTrue(indexedChunkRepository.findByBlobUri("http://chunk-blob").isEmpty());
    }

    @Test
    void testIndexAuditLogRepository() {
        IndexAuditLog log = IndexAuditLog.builder()
                .blobUri("http://audit-blob")
                .action("INDEX")
                .status("success")
                .createdAt(OffsetDateTime.now())
                .build();

        indexAuditLogRepository.save(log);

        List<IndexAuditLog> logs = indexAuditLogRepository.findTop100ByBlobUriOrderByCreatedAtDesc("http://audit-blob");
        assertEquals(1, logs.size());
        assertEquals("INDEX", logs.get(0).getAction());
    }
}

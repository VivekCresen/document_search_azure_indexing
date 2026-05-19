package com.cresensolutions.document_search_azure_indexing.dto;

import com.cresensolutions.document_search_azure_indexing.domain.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DomainAndDtoTest {

    @Test
    void testFileInIndex() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID uuid = UUID.randomUUID();

        FileInIndex file1 = FileInIndex.builder()
                .id(1L)
                .blobUri("http://blob1")
                .fileName("file1.pdf")
                .status("stable")
                .folderId(10L)
                .indexedBy(uuid)
                .lastModifiedBlob(now)
                .updatedAt(now)
                .build();

        assertEquals(1L, file1.getId());
        assertEquals("http://blob1", file1.getBlobUri());
        assertEquals("file1.pdf", file1.getFileName());
        assertEquals("stable", file1.getStatus());
        assertEquals(10L, file1.getFolderId());
        assertEquals(uuid, file1.getIndexedBy());
        assertEquals(now, file1.getLastModifiedBlob());
        assertEquals(now, file1.getUpdatedAt());

        FileInIndex file2 = new FileInIndex();
        file2.setId(1L);
        file2.setBlobUri("http://blob1");
        file2.setFileName("file1.pdf");
        file2.setStatus("stable");
        file2.setFolderId(10L);
        file2.setIndexedBy(uuid);
        file2.setLastModifiedBlob(now);
        file2.setUpdatedAt(now);

        assertEquals(file1, file2);
        assertEquals(file1.hashCode(), file2.hashCode());
        assertNotNull(file1.toString());

        // Cover builder default constructor/no-args and all-args explicitly
        FileInIndex defaultFile = new FileInIndex();
        assertNotNull(defaultFile.getStatus());
        assertNotNull(defaultFile.getUpdatedAt());
    }

    @Test
    void testIngestionJob() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID uuid = UUID.randomUUID();

        IngestionJob job1 = IngestionJob.builder()
                .id(1L)
                .blobUri("http://blob1")
                .blobName("folder/file1.pdf")
                .fileName("file1.pdf")
                .status("to_be_ingested")
                .attempts(1)
                .maxAttempts(3)
                .errorMessage("error")
                .chunkCount(5)
                .folderId(10L)
                .blobLastModified(now)
                .blobEtag("etag")
                .blobSizeBytes(100L)
                .indexedBy(uuid)
                .lockedBy("worker")
                .lockedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .completedAt(now)
                .build();

        assertEquals(1L, job1.getId());
        assertEquals("http://blob1", job1.getBlobUri());
        assertEquals("folder/file1.pdf", job1.getBlobName());
        assertEquals("file1.pdf", job1.getFileName());
        assertEquals("to_be_ingested", job1.getStatus());
        assertEquals(1, job1.getAttempts());
        assertEquals(3, job1.getMaxAttempts());
        assertEquals("error", job1.getErrorMessage());
        assertEquals(5, job1.getChunkCount());
        assertEquals(10L, job1.getFolderId());
        assertEquals(now, job1.getBlobLastModified());
        assertEquals("etag", job1.getBlobEtag());
        assertEquals(100L, job1.getBlobSizeBytes());
        assertEquals(uuid, job1.getIndexedBy());
        assertEquals("worker", job1.getLockedBy());
        assertEquals(now, job1.getLockedAt());
        assertEquals(now, job1.getCreatedAt());
        assertEquals(now, job1.getUpdatedAt());
        assertEquals(now, job1.getCompletedAt());

        IngestionJob job2 = new IngestionJob();
        job2.setId(1L);
        job2.setBlobUri("http://blob1");
        job2.setBlobName("folder/file1.pdf");
        job2.setFileName("file1.pdf");
        job2.setStatus("to_be_ingested");
        job2.setAttempts(1);
        job2.setMaxAttempts(3);
        job2.setErrorMessage("error");
        job2.setChunkCount(5);
        job2.setFolderId(10L);
        job2.setBlobLastModified(now);
        job2.setBlobEtag("etag");
        job2.setBlobSizeBytes(100L);
        job2.setIndexedBy(uuid);
        job2.setLockedBy("worker");
        job2.setLockedAt(now);
        job2.setCreatedAt(now);
        job2.setUpdatedAt(now);
        job2.setCompletedAt(now);

        assertEquals(job1, job2);
        assertEquals(job1.hashCode(), job2.hashCode());
        assertNotNull(job1.toString());
    }

    @Test
    void testIndexedChunk() {
        OffsetDateTime now = OffsetDateTime.now();
        IngestionJob job = new IngestionJob();

        IndexedChunk chunk1 = IndexedChunk.builder()
                .id(1L)
                .job(job)
                .blobUri("http://blob1")
                .searchDocumentId("doc1")
                .chunkNumber(2)
                .pageNumber(3)
                .contentHash("hash")
                .createdAt(now)
                .build();

        assertEquals(1L, chunk1.getId());
        assertEquals(job, chunk1.getJob());
        assertEquals("http://blob1", chunk1.getBlobUri());
        assertEquals("doc1", chunk1.getSearchDocumentId());
        assertEquals(2, chunk1.getChunkNumber());
        assertEquals(3, chunk1.getPageNumber());
        assertEquals("hash", chunk1.getContentHash());
        assertEquals(now, chunk1.getCreatedAt());

        IndexedChunk chunk2 = new IndexedChunk();
        chunk2.setId(1L);
        chunk2.setJob(job);
        chunk2.setBlobUri("http://blob1");
        chunk2.setSearchDocumentId("doc1");
        chunk2.setChunkNumber(2);
        chunk2.setPageNumber(3);
        chunk2.setContentHash("hash");
        chunk2.setCreatedAt(now);

        assertEquals(chunk1, chunk2);
        assertEquals(chunk1.hashCode(), chunk2.hashCode());
        assertNotNull(chunk1.toString());
    }

    @Test
    void testIndexAuditLog() {
        OffsetDateTime now = OffsetDateTime.now();
        IngestionJob job = new IngestionJob();
        Map<String, Object> metadata = Map.of("key", "value");

        IndexAuditLog log1 = IndexAuditLog.builder()
                .id(1L)
                .job(job)
                .blobUri("http://blob1")
                .action("action")
                .status("status")
                .message("message")
                .metadata(metadata)
                .createdAt(now)
                .build();

        assertEquals(1L, log1.getId());
        assertEquals(job, log1.getJob());
        assertEquals("http://blob1", log1.getBlobUri());
        assertEquals("action", log1.getAction());
        assertEquals("status", log1.getStatus());
        assertEquals("message", log1.getMessage());
        assertEquals(metadata, log1.getMetadata());
        assertEquals(now, log1.getCreatedAt());

        IndexAuditLog log2 = new IndexAuditLog();
        log2.setId(1L);
        log2.setJob(job);
        log2.setBlobUri("http://blob1");
        log2.setAction("action");
        log2.setStatus("status");
        log2.setMessage("message");
        log2.setMetadata(metadata);
        log2.setCreatedAt(now);

        assertEquals(log1, log2);
        assertEquals(log1.hashCode(), log2.hashCode());
        assertNotNull(log1.toString());
    }

    @Test
    void testRecords() {
        OffsetDateTime now = OffsetDateTime.now();

        // TriggerIndexRequest
        TriggerIndexRequest trigger = new TriggerIndexRequest("uri", "name", "file");
        assertEquals("uri", trigger.blobUri());
        assertEquals("name", trigger.blobName());
        assertEquals("file", trigger.fileName());
        assertNotNull(trigger.toString());

        // BlobInventoryItem
        BlobInventoryItem item = new BlobInventoryItem("uri", "name", "file", now, 100L, "etag");
        assertEquals("uri", item.blobUri());
        assertEquals("name", item.blobName());
        assertEquals("file", item.fileName());
        assertEquals(now, item.lastModified());
        assertEquals(100L, item.sizeBytes());
        assertEquals("etag", item.etag());
        assertNotNull(item.toString());

        // BlobScanResult
        BlobScanResult scan = new BlobScanResult(1, 2, 3, 4, List.of("ingest"), List.of("delete"));
        assertEquals(1, scan.scanned());
        assertEquals(2, scan.queuedForIngestion());
        assertEquals(3, scan.queuedForDeletion());
        assertEquals(4, scan.unchanged());
        assertEquals(List.of("ingest"), scan.ingestionBlobUris());
        assertEquals(List.of("delete"), scan.deletionBlobUris());
        assertNotNull(scan.toString());

        // DownloadedBlob
        Path path = Path.of("temp.txt");
        DownloadedBlob download = new DownloadedBlob("uri", "name", "file", path);
        assertEquals("uri", download.blobUri());
        assertEquals("name", download.blobName());
        assertEquals("file", download.fileName());
        assertEquals(path, download.path());
        assertNotNull(download.toString());

        // DiSpan
        DiSpan span = new DiSpan(1, "text", List.of(1.0, 2.0));
        assertEquals(1, span.page());
        assertEquals("text", span.paragraphText());
        assertEquals(List.of(1.0, 2.0), span.polygon());
        assertNotNull(span.toString());

        // ParsedDocument
        ParsedDocument doc = new ParsedDocument("text", "title", Map.of("key", "val"), List.of(span));
        assertEquals("text", doc.text());
        assertEquals("title", doc.title());
        assertEquals(Map.of("key", "val"), doc.metadata());
        assertEquals(List.of(span), doc.diSpans());
        assertNotNull(doc.toString());

        // DocumentChunk
        DocumentChunk chunk = new DocumentChunk(1, "content", "title", 2, Map.of("key", "val"), List.of(span));
        assertEquals(1, chunk.chunkNumber());
        assertEquals("content", chunk.content());
        assertEquals("title", chunk.title());
        assertEquals(2, chunk.pageNumber());
        assertEquals(Map.of("key", "val"), chunk.metadata());
        assertEquals(List.of(span), chunk.diSpans());
        assertNotNull(chunk.toString());

        // EnrichmentResult
        EnrichmentResult enrichment = new EnrichmentResult(List.of("topic"), List.of("query"), List.of("signal"));
        assertEquals(List.of("topic"), enrichment.topics());
        assertEquals(List.of("query"), enrichment.exampleQueries());
        assertEquals(List.of("signal"), enrichment.intentSignals());
        assertNotNull(enrichment.toString());

        EnrichmentResult empty = EnrichmentResult.empty();
        assertTrue(empty.topics().isEmpty());
        assertTrue(empty.exampleQueries().isEmpty());
        assertEquals(List.of("unknown"), empty.intentSignals());

        // JobProcessResult
        JobProcessResult processResult = new JobProcessResult(5, 4, 1);
        assertEquals(5, processResult.processed());
        assertEquals(4, processResult.succeeded());
        assertEquals(1, processResult.failed());
        assertNotNull(processResult.toString());
    }
}

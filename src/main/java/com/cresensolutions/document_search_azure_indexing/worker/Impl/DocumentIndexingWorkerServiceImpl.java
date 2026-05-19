package com.cresensolutions.document_search_azure_indexing.worker.Impl;

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
import com.cresensolutions.document_search_azure_indexing.worker.DocumentIndexingWorkerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cresensolutions.document_search_azure_indexing.commons.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentIndexingWorkerServiceImpl implements DocumentIndexingWorkerService {

    private final IngestionJobRepository ingestionJobRepository;
    private final FileInIndexRepository fileInIndexRepository;
    private final IndexedChunkRepository indexedChunkRepository;
    private final IndexAuditLogRepository auditLogRepository;
    private final BlobDownloadService blobDownloadService;
    private final DocumentParserService documentParserService;
    private final DocumentChunkingService documentChunkingService;
    private final MetadataEnrichmentService metadataEnrichmentService;
    private final AzureSearchIndexService azureSearchIndexService;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final ObjectMapper objectMapper;

    @Override
    public JobProcessResult processQueuedJobs(int maxJobs) {
        List<IngestionJob> ingestionJobs = lockJobs(Constants.JOB_STATUS_TO_BE_INGESTED, maxJobs);
        List<IngestionJob> deletionJobs = lockJobs(Constants.JOB_STATUS_TO_BE_DELETED, Math.max(0, maxJobs - ingestionJobs.size()));

        long deletionSucceeded = deletionJobs.parallelStream().filter(job -> {
            try {
                processDeletion(job);
                return true;
            } catch (Exception e) {
                failJob(job, e);
                return false;
            }
        }).count();

        long ingestionSucceeded = ingestionJobs.parallelStream().filter(job -> {
            try {
                processIngestion(job);
                return true;
            } catch (Exception e) {
                failJob(job, e);
                return false;
            }
        }).count();

        long totalSucceeded = deletionSucceeded + ingestionSucceeded;
        long totalFailed = (deletionJobs.size() + ingestionJobs.size()) - totalSucceeded;

        return new JobProcessResult(ingestionJobs.size() + deletionJobs.size(), totalSucceeded, totalFailed);
    }

    @Override
    @Transactional
    public List<IngestionJob> lockJobs(String status, int maxJobs) {
        if (maxJobs <= 0) {
            return List.of();
        }
        String lockedStatus = Constants.JOB_STATUS_TO_BE_DELETED.equals(status) ? Constants.JOB_STATUS_DELETE_INP : Constants.JOB_STATUS_INGESTION_INP;
        List<IngestionJob> jobs = ingestionJobRepository.findByStatusOrderByCreatedAtAsc(
                status,
                PageRequest.of(0, maxJobs)
        );
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jobs.forEach(job -> {
            job.setStatus(lockedStatus);
            job.setLockedBy(hostName());
            job.setLockedAt(now);
            job.setAttempts(job.getAttempts() + 1);
            job.setUpdatedAt(now);
        });
        return ingestionJobRepository.saveAll(jobs);
    }

    private void processIngestion(IngestionJob job) throws Exception {
        DownloadedBlob downloadedBlob = null;
        try {
            downloadedBlob = blobDownloadService.download(job.getBlobUri(), job.getBlobName(), job.getFileName());
            ParsedDocument parsedDocument = documentParserService.parse(downloadedBlob);
            List<DocumentChunk> chunks = documentChunkingService.chunk(parsedDocument);
            if (chunks.isEmpty()) {
                throw new IllegalStateException("No chunks produced from parsed document");
            }

            List<Map<String, Object>> searchDocuments = new ArrayList<>();
            List<IndexedChunk> indexedChunks = new ArrayList<>();
            EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
            if (embeddingModel == null) {
                throw new IllegalStateException("Spring AI EmbeddingModel is not configured");
            }

            for (DocumentChunk chunk : chunks) {
                EnrichmentResult enrichment = metadataEnrichmentService.enrich(chunk.content());
                String searchDocumentId = createDocumentId(job.getBlobUri(), chunk.chunkNumber());
                searchDocuments.add(toSearchDocument(job, chunk, enrichment, embeddingModel.embed(chunk.content()), searchDocumentId));
                indexedChunks.add(IndexedChunk.builder()
                        .job(job)
                        .blobUri(job.getBlobUri())
                        .searchDocumentId(searchDocumentId)
                        .chunkNumber(chunk.chunkNumber())
                        .pageNumber(chunk.pageNumber())
                        .contentHash(sha256(chunk.content()))
                        .build());
            }

            azureSearchIndexService.uploadDocuments(searchDocuments);
            indexedChunkRepository.deleteByBlobUri(job.getBlobUri());
            indexedChunkRepository.saveAll(indexedChunks);
            completeJob(job, chunks.size());
        } finally {
            blobDownloadService.cleanup(downloadedBlob);
        }
    }

    private void processDeletion(IngestionJob job) {
        List<String> documentIds = indexedChunkRepository.findByBlobUri(job.getBlobUri()).stream()
                .map(IndexedChunk::getSearchDocumentId)
                .toList();
        azureSearchIndexService.deleteDocumentsByIds(documentIds);
        indexedChunkRepository.deleteByBlobUri(job.getBlobUri());
        fileInIndexRepository.findByBlobUri(job.getBlobUri()).ifPresent(fileInIndexRepository::delete);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        job.setStatus(Constants.JOB_STATUS_DELETED);
        job.setCompletedAt(now);
        job.setUpdatedAt(now);
        ingestionJobRepository.save(job);
        audit(job, Constants.AUDIT_ACTION_DELETE_EXECUTION, Constants.JOB_STATUS_DELETED, "Deleted document chunks from Azure Search", Map.of("deleted_chunks", documentIds.size()));
    }

    private Map<String, Object> toSearchDocument(
            IngestionJob job,
            DocumentChunk chunk,
            EnrichmentResult enrichment,
            float[] embedding,
            String searchDocumentId
    ) throws Exception {
        Map<String, Object> metadata = new LinkedHashMap<>(chunk.metadata());
        metadata.put("url", job.getBlobUri());
        metadata.put("blob_uri", job.getBlobUri());
        metadata.put("source", job.getFileName());
        metadata.put("page_number", chunk.pageNumber());
        metadata.put("di_page_spans", chunk.diSpans());

        String fileName = job.getFileName() == null ? job.getBlobUri() : job.getFileName();
        String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase() : "";

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", searchDocumentId);
        document.put("content", chunk.content());
        document.put("title", chunk.title());
        document.put("source", fileName);
        document.put("filepath", job.getBlobUri());
        document.put("file_type", extension);
        document.put("chunk_id", String.valueOf(chunk.chunkNumber()));
        document.put("page_number", chunk.pageNumber());
        document.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
        document.put("metadata", objectMapper.writeValueAsString(metadata));
        document.put("topics", enrichment.topics());
        document.put("example_queries", enrichment.exampleQueries());
        document.put("intent_signals", enrichment.intentSignals());
        document.put("folder_id", job.getFolderId() == null ? "0" : String.valueOf(job.getFolderId()));
        document.put("blob_uri", job.getBlobUri());
        document.put("embedding", toFloatList(embedding));
        return document;
    }

    private List<Float> toFloatList(float[] embedding) {
        List<Float> values = new ArrayList<>(embedding.length);
        for (float value : embedding) {
            values.add(value);
        }
        return values;
    }

    @Override
    @Transactional
    public void completeJob(IngestionJob job, int chunkCount) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        job.setStatus(Constants.JOB_STATUS_STABLE);
        job.setChunkCount(chunkCount);
        job.setErrorMessage(null);
        job.setCompletedAt(now);
        job.setUpdatedAt(now);
        ingestionJobRepository.save(job);

        FileInIndex file = fileInIndexRepository.findByBlobUri(job.getBlobUri())
                .orElseGet(() -> FileInIndex.builder().blobUri(job.getBlobUri()).build());
        file.setFileName(job.getFileName());
        file.setFolderId(job.getFolderId());
        file.setIndexedBy(job.getIndexedBy());
        file.setLastModifiedBlob(job.getBlobLastModified());
        file.setStatus(Constants.JOB_STATUS_STABLE);
        file.setUpdatedAt(now);
        fileInIndexRepository.save(file);

        audit(job, Constants.AUDIT_ACTION_INDEX_EXECUTION, Constants.JOB_STATUS_STABLE, "Indexed document chunks into Azure Search", Map.of("chunk_count", chunkCount));
    }

    @Override
    @Transactional
    public void failJob(IngestionJob job, Exception e) {
        log.warn("Indexing job failed for {}", job.getBlobUri(), e);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        job.setStatus(job.getAttempts() >= job.getMaxAttempts() ? Constants.JOB_STATUS_FAILED : Constants.JOB_STATUS_TO_BE_INGESTED);
        job.setErrorMessage(e.getMessage());
        job.setUpdatedAt(now);
        ingestionJobRepository.save(job);

        fileInIndexRepository.findByBlobUri(job.getBlobUri()).ifPresent(file -> {
            file.setStatus(job.getStatus());
            file.setUpdatedAt(now);
            fileInIndexRepository.save(file);
        });

        audit(job, Constants.AUDIT_ACTION_INDEX_EXECUTION, job.getStatus(), e.getMessage(), null);
    }

    private void audit(IngestionJob job, String action, String status, String message, Map<String, Object> metadata) {
        auditLogRepository.save(IndexAuditLog.builder()
                .job(job)
                .blobUri(job.getBlobUri())
                .action(action)
                .status(status)
                .message(message)
                .metadata(metadata)
                .build());
    }

    private String createDocumentId(String blobUri, int chunkNumber) {
        String raw = blobUri + "-Chunk-" + chunkNumber;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(String content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    }

    private String hostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return Constants.HOSTNAME_FALLBACK;
        }
    }
}

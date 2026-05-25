package com.cresensolutions.document_search_azure_indexing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.cresensolutions.document_search_azure_indexing.commons.Constants;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ingestion_jobs", schema = "demo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blob_uri", nullable = false, unique = true, columnDefinition = "text")
    private String blobUri;

    @Column(name = "blob_name", columnDefinition = "text")
    private String blobName;

    @Column(name = "file_name", columnDefinition = "text")
    private String fileName;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = Constants.JOB_STATUS_TO_BE_INGESTED;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Integer maxAttempts = 3;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "chunk_count", nullable = false)
    @Builder.Default
    private Integer chunkCount = 0;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "blob_last_modified")
    private OffsetDateTime blobLastModified;

    @Column(name = "blob_etag", columnDefinition = "text")
    private String blobEtag;

    @Column(name = "blob_size_bytes")
    private Long blobSizeBytes;

    @Column(name = "indexed_by")
    private UUID indexedBy;

    @Column(name = "locked_by")
    private String lockedBy;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}

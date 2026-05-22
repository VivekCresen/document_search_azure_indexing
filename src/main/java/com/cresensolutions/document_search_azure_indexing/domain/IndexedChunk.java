package com.cresensolutions.document_search_azure_indexing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "indexed_chunks", schema = "demo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexedChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private IngestionJob job;

    @Column(name = "blob_uri", nullable = false, columnDefinition = "text")
    private String blobUri;

    @Column(name = "search_document_id", nullable = false, unique = true, columnDefinition = "text")
    private String searchDocumentId;

    @Column(name = "chunk_number", nullable = false)
    private Integer chunkNumber;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "content_hash", length = 128)
    private String contentHash;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

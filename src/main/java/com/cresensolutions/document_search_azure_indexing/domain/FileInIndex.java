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

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "files_in_index", schema = "prestage")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blob_uri", nullable = false, unique = true, columnDefinition = "text")
    private String blobUri;

    @Column(name = "file_name", columnDefinition = "text")
    private String fileName;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "to_be_ingested";

    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "indexed_by")
    private UUID indexedBy;

    @Column(name = "last_modified_blob")
    private OffsetDateTime lastModifiedBlob;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}

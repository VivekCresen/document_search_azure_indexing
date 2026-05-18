package com.cresensolutions.document_search_azure_indexing.repository;

import com.cresensolutions.document_search_azure_indexing.domain.IndexedChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IndexedChunkRepository extends JpaRepository<IndexedChunk, Long> {

    List<IndexedChunk> findByBlobUri(String blobUri);

    void deleteByBlobUri(String blobUri);
}

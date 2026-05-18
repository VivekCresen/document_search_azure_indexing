package com.cresensolutions.document_search_azure_indexing.repository;

import com.cresensolutions.document_search_azure_indexing.domain.IngestionJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, Long> {

    Optional<IngestionJob> findByBlobUri(String blobUri);

    List<IngestionJob> findTop100ByStatusOrderByCreatedAtAsc(String status);

    List<IngestionJob> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    long countByStatus(String status);
}

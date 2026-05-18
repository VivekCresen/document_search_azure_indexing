package com.cresensolutions.document_search_azure_indexing.repository;

import com.cresensolutions.document_search_azure_indexing.domain.IndexAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IndexAuditLogRepository extends JpaRepository<IndexAuditLog, Long> {

    List<IndexAuditLog> findTop100ByBlobUriOrderByCreatedAtDesc(String blobUri);
}

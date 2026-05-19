package com.cresensolutions.document_search_azure_indexing.repository;

import com.cresensolutions.document_search_azure_indexing.domain.IndexAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository interface for managing persistent {@link IndexAuditLog} records.
 * Keeps an audit history of indexing operations, ingestion, and deletion events.
 */
public interface IndexAuditLogRepository extends JpaRepository<IndexAuditLog, Long> {

    /**
     * Retrieves the latest 100 audit logs matching a specific blob URI,
     * ordered by creation timestamp descending.
     *
     * @param blobUri the target document's blob storage URI
     * @return a list of up to 100 matching audit log entries
     */
    List<IndexAuditLog> findTop100ByBlobUriOrderByCreatedAtDesc(String blobUri);
}

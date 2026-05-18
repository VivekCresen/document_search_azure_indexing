package com.cresensolutions.document_search_azure_indexing.repository;

import com.cresensolutions.document_search_azure_indexing.domain.FileInIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FileInIndexRepository extends JpaRepository<FileInIndex, Long> {

    Optional<FileInIndex> findByBlobUri(String blobUri);

    List<FileInIndex> findByStatus(String status);

    @Query("select f.blobUri from FileInIndex f where f.status not in ('to_be_deleted', 'delete_inp', 'deleted')")
    List<String> findActiveBlobUris();
}

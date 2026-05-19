package com.cresensolutions.document_search_azure_indexing.search;

import java.util.List;
import java.util.Map;

public interface AzureSearchIndexService {
    void createOrUpdateIndex();
    void uploadDocuments(List<Map<String, Object>> documents);
    void deleteDocumentsByIds(List<String> documentIds);
}

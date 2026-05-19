package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.dto.DocumentChunk;
import com.cresensolutions.document_search_azure_indexing.dto.ParsedDocument;
import java.util.List;

public interface DocumentChunkingService {
    List<DocumentChunk> chunk(ParsedDocument parsedDocument);
}

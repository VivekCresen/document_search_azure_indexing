package com.cresensolutions.document_search_azure_indexing.dto;

import java.util.List;
import java.util.Map;

public record DocumentChunk(
        int chunkNumber,
        String content,
        String title,
        int pageNumber,
        Map<String, Object> metadata,
        List<DiSpan> diSpans
) {
}

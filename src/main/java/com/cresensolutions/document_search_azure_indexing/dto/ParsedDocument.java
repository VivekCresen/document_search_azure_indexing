package com.cresensolutions.document_search_azure_indexing.dto;

import java.util.List;
import java.util.Map;

public record ParsedDocument(
        String text,
        String title,
        Map<String, Object> metadata,
        List<DiSpan> diSpans
) {
}

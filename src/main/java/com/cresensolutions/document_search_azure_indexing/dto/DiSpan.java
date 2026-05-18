package com.cresensolutions.document_search_azure_indexing.dto;

import java.util.List;

public record DiSpan(
        Integer page,
        String paragraphText,
        List<Double> polygon
) {
}

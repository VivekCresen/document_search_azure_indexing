package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.dto.DiSpan;
import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.dto.DocumentChunk;
import com.cresensolutions.document_search_azure_indexing.dto.ParsedDocument;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentChunkingService {

    private final AzureIndexingProperties properties;

    public DocumentChunkingService(AzureIndexingProperties properties) {
        this.properties = properties;
    }

    public List<DocumentChunk> chunk(ParsedDocument parsedDocument) {
        int chunkSize = Math.max(500, properties.settings().chunkSize());
        int overlap = Math.max(0, Math.min(properties.settings().chunkOverlap(), chunkSize / 2));
        String text = parsedDocument.text();
        List<DocumentChunk> chunks = new ArrayList<>();

        int start = 0;
        int chunkNumber = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + chunkSize);
            if (end < text.length()) {
                int paragraphBreak = text.lastIndexOf("\n\n", end);
                if (paragraphBreak > start + chunkSize / 2) {
                    end = paragraphBreak;
                } else {
                    int sentenceBreak = text.lastIndexOf(". ", end);
                    if (sentenceBreak > start + chunkSize / 2) {
                        end = sentenceBreak + 1;
                    }
                }
            }

            String content = text.substring(start, end).trim();
            if (!content.isBlank()) {
                Map<String, Object> metadata = new HashMap<>(parsedDocument.metadata());
                List<DiSpan> chunkSpans = matchingSpans(content, parsedDocument.diSpans());
                int pageNumber = chunkSpans.isEmpty() ? intMetadata(metadata.get("page_number"), 0) : chunkSpans.get(0).page();
                metadata.put("page_number", pageNumber);
                metadata.put("di_page_spans", chunkSpans);
                chunks.add(new DocumentChunk(
                        chunkNumber,
                        content,
                        parsedDocument.title(),
                        pageNumber,
                        metadata,
                        chunkSpans
                ));
                chunkNumber++;
            }

            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }

        return chunks;
    }

    private List<DiSpan> matchingSpans(String chunkText, List<DiSpan> spans) {
        if (spans == null || spans.isEmpty()) {
            return List.of();
        }
        return spans.stream()
                .filter(span -> belongsToChunk(chunkText, span.paragraphText()))
                .toList();
    }

    private boolean belongsToChunk(String chunkText, String paragraphText) {
        if (paragraphText == null || paragraphText.isBlank()) {
            return false;
        }
        String normalizedParagraph = paragraphText.trim();
        if (chunkText.contains(normalizedParagraph)) {
            return true;
        }
        if (normalizedParagraph.length() <= 20) {
            return false;
        }
        String fingerprint = normalizedParagraph.substring(0, Math.min(60, normalizedParagraph.length())).trim();
        return !fingerprint.isBlank() && chunkText.contains(fingerprint);
    }

    private int intMetadata(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }
}

package com.cresensolutions.document_search_azure_indexing.service;

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
                metadata.put("page_number", 0);
                chunks.add(new DocumentChunk(
                        chunkNumber,
                        content,
                        parsedDocument.title(),
                        0,
                        metadata,
                        parsedDocument.diSpans()
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
}

package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.service.Impl.DocumentChunkingServiceImpl;
import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.dto.DiSpan;
import com.cresensolutions.document_search_azure_indexing.dto.DocumentChunk;
import com.cresensolutions.document_search_azure_indexing.dto.ParsedDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentChunkingServiceTest {

    @Mock
    private AzureIndexingProperties properties;

    @Mock
    private AzureIndexingProperties.Settings settings;

    @InjectMocks
    private DocumentChunkingServiceImpl service;

    @Test
    void testChunkShortDocument() {
        when(properties.settings()).thenReturn(settings);
        when(settings.chunkSize()).thenReturn(1000);
        when(settings.chunkOverlap()).thenReturn(100);

        ParsedDocument parsedDocument = new ParsedDocument(
                "Short content.", "Doc Title", Map.of("page_number", 1), List.of()
        );

        List<DocumentChunk> chunks = service.chunk(parsedDocument);

        assertEquals(1, chunks.size());
        assertEquals("Short content.", chunks.get(0).content());
        assertEquals("Doc Title", chunks.get(0).title());
        assertEquals(1, chunks.get(0).pageNumber());
    }

    @Test
    void testChunkLongDocumentWithParagraphAndSentenceSplits() {
        when(properties.settings()).thenReturn(settings);
        // Make chunk size small to force splits
        when(settings.chunkSize()).thenReturn(500); // 500 is the minimum
        when(settings.chunkOverlap()).thenReturn(50);

        // We construct a text of length 1100.
        // It has a paragraph break "\n\n" around character 400.
        // It has a sentence break ". " around character 800.
        String p1 = "This is a very long paragraph that serves as paragraph one. ".repeat(6).trim(); // ~360 chars
        String p2 = "Paragraph two is also very long and serves to test splitting logic properly. ".repeat(6).trim(); // ~450 chars
        String p3 = "Finally paragraph three contains concluding sentences. Let us verify if chunking fits. ".repeat(4).trim(); // ~350 chars

        String fullText = p1 + "\n\n" + p2 + ". " + p3;

        DiSpan span1 = new DiSpan(1, "This is a very long paragraph that serves as paragraph one.", List.of());
        // For fingerprint match: paragraphText longer than 20, doesn't match exactly, but its fingerprint (first 60 chars) matches
        DiSpan span2 = new DiSpan(2, "Paragraph two is also very long and serves to test splitting logic properly. Additional text that isn't in the chunk.", List.of());
        // Span with short paragraph text (<= 20)
        DiSpan span3 = new DiSpan(3, "short", List.of());
        // Span with blank paragraph text
        DiSpan span4 = new DiSpan(3, "  ", List.of());

        ParsedDocument parsedDocument = new ParsedDocument(
                fullText, "Multi-Split Title", Map.of("page_number", "NotANumber"), List.of(span1, span2, span3, span4)
        );

        List<DocumentChunk> chunks = service.chunk(parsedDocument);

        assertTrue(chunks.size() >= 2);
        // Page number fallback (since metadata contains non-number "NotANumber" and first chunk matching span has page 1)
        assertEquals(1, chunks.get(0).pageNumber());

        // Verify some chunks contain spans
        assertNotNull(chunks.get(0).diSpans());
    }

    @Test
    void testBelongsToChunkEdgeCases() {
        when(properties.settings()).thenReturn(settings);
        when(settings.chunkSize()).thenReturn(500);
        when(settings.chunkOverlap()).thenReturn(100);

        ParsedDocument parsedDocument = new ParsedDocument(
                "Short chunk of text.", "Title", Map.of("page_number", 5L), List.of()
        );

        List<DocumentChunk> chunks = service.chunk(parsedDocument);

        assertEquals(1, chunks.size());
        assertEquals(5, chunks.get(0).pageNumber());
    }
}

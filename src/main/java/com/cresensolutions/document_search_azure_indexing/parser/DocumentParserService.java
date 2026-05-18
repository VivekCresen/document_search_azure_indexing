package com.cresensolutions.document_search_azure_indexing.parser;

import com.cresensolutions.document_search_azure_indexing.dto.DownloadedBlob;
import com.cresensolutions.document_search_azure_indexing.dto.ParsedDocument;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentParserService {

    private final AutoDetectParser parser = new AutoDetectParser();

    public ParsedDocument parse(DownloadedBlob downloadedBlob) {
        try (InputStream inputStream = Files.newInputStream(downloadedBlob.path())) {
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata tikaMetadata = new Metadata();
            parser.parse(inputStream, handler, tikaMetadata, new ParseContext());

            String text = normalize(handler.toString());
            if (text.isBlank()) {
                throw new IllegalStateException("No extractable text found in " + downloadedBlob.fileName());
            }

            Map<String, Object> metadata = new HashMap<>();
            for (String name : tikaMetadata.names()) {
                metadata.put(name, tikaMetadata.get(name));
            }
            metadata.put("blob_uri", downloadedBlob.blobUri());
            metadata.put("source", downloadedBlob.fileName());
            metadata.put("di_processed", false);
            metadata.put("di_page_spans", List.of());

            return new ParsedDocument(text, extractTitle(text, downloadedBlob.fileName()), metadata, List.of());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse " + downloadedBlob.fileName(), e);
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private String extractTitle(String text, String fallback) {
        return text.lines()
                .map(String::trim)
                .filter(line -> line.length() > 10)
                .findFirst()
                .map(line -> line.substring(0, Math.min(line.length(), 100)))
                .orElse(fallback);
    }
}

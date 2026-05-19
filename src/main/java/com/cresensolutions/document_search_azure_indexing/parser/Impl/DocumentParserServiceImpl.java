package com.cresensolutions.document_search_azure_indexing.parser.Impl;

import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.dto.DiSpan;
import com.cresensolutions.document_search_azure_indexing.dto.DownloadedBlob;
import com.cresensolutions.document_search_azure_indexing.dto.ParsedDocument;
import com.cresensolutions.document_search_azure_indexing.parser.DocumentParserService;
import com.cresensolutions.document_search_azure_indexing.utils.CommonUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of {@link DocumentParserService} using a hybrid strategy:
 * attempts Azure Document Intelligence for supported high-fidelity formats,
 * and falls back to Apache Tika for local cross-platform text extraction.
 */
@Service
public class DocumentParserServiceImpl implements DocumentParserService {

    /** Set of file extensions natively supported by Azure Document Intelligence */
    private static final Set<String> DOCUMENT_INTELLIGENCE_EXTENSIONS = Set.of(
            ".pdf", ".docx", ".doc", ".png", ".jpg", ".jpeg", ".tiff", ".bmp"
    );

    private final AutoDetectParser parser = new AutoDetectParser();
    private final AzureIndexingProperties properties;
    private final RestClient restClient;

    /**
     * Constructs a DocumentParserServiceImpl with necessary configurations.
     *
     * @param properties indexing configuration properties
     * @param restClientBuilder building utility for REST API consumption
     */
    public DocumentParserServiceImpl(AzureIndexingProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    /**
     * Parses the text content from the specified local downloaded blob, checking
     * Document Intelligence support before falling back to local Apache Tika.
     *
     * @param downloadedBlob the local reference of the downloaded storage blob
     * @return the parsed document holding text, title, and metadata Map
     */
    @Override
    public ParsedDocument parse(DownloadedBlob downloadedBlob) {
        if (isDocumentIntelligenceEnabled() && isDocumentIntelligenceSupported(downloadedBlob.fileName())) {
            try {
                return parseWithDocumentIntelligence(downloadedBlob);
            } catch (Exception ignored) {
                // Fail gracefully and fall back to local parser
            }
        }
        return parseWithTika(downloadedBlob);
    }

    /**
     * Extracts text from the downloaded file using local Apache Tika library.
     */
    private ParsedDocument parseWithTika(DownloadedBlob downloadedBlob) {
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

    private ParsedDocument parseWithDocumentIntelligence(DownloadedBlob downloadedBlob) throws Exception {
        String submitUrl = "%s/documentintelligence/documentModels/prebuilt-read:analyze?api-version=%s".formatted(
                CommonUtils.trimTrailingSlash(properties.documentIntelligence().endpoint()),
                properties.documentIntelligence().apiVersion()
        );
        String base64Source = Base64.getEncoder().encodeToString(Files.readAllBytes(downloadedBlob.path()));

        ResponseEntity<Void> response = restClient.post()
                .uri(submitUrl)
                .header("Ocp-Apim-Subscription-Key", properties.documentIntelligence().apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("base64Source", base64Source))
                .retrieve()
                .toBodilessEntity();

        String operationUrl = response.getHeaders().getFirst("Operation-Location");
        if (!StringUtils.hasText(operationUrl)) {
            throw new IllegalStateException("Document Intelligence did not return an Operation-Location header");
        }

        Map<String, Object> analyzeResult = pollDocumentIntelligenceOperation(operationUrl);
        return toParsedDocument(downloadedBlob, analyzeResult);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pollDocumentIntelligenceOperation(String operationUrl) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        while (System.nanoTime() < deadline) {
            Map<String, Object> response = restClient.get()
                    .uri(operationUrl)
                    .header("Ocp-Apim-Subscription-Key", properties.documentIntelligence().apiKey())
                    .retrieve()
                    .body(Map.class);

            String status = response == null ? "" : String.valueOf(response.getOrDefault("status", ""));
            if ("succeeded".equalsIgnoreCase(status)) {
                Object analyzeResult = response.get("analyzeResult");
                return analyzeResult instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            }
            if ("failed".equalsIgnoreCase(status)) {
                throw new IllegalStateException("Document Intelligence operation failed: " + response.get("error"));
            }
            Thread.sleep(3000);
        }
        throw new IllegalStateException("Document Intelligence operation timed out");
    }

    @SuppressWarnings("unchecked")
    private ParsedDocument toParsedDocument(DownloadedBlob downloadedBlob, Map<String, Object> analyzeResult) {
        List<Map<String, Object>> paragraphs = listOfMaps(analyzeResult.get("paragraphs"));
        List<DiSpan> spans = new ArrayList<>();
        List<String> textParts = new ArrayList<>();

        for (Map<String, Object> paragraph : paragraphs) {
            String paragraphText = String.valueOf(paragraph.getOrDefault("content", "")).trim();
            if (!StringUtils.hasText(paragraphText)) {
                continue;
            }

            int page = 1;
            List<Double> polygon = List.of();
            List<Map<String, Object>> regions = listOfMaps(paragraph.get("boundingRegions"));
            if (!regions.isEmpty()) {
                Map<String, Object> region = regions.get(0);
                page = intValue(region.get("pageNumber"), 1);
                polygon = numberList(region.get("polygon"));
            }

            spans.add(new DiSpan(page, paragraphText, polygon));
            textParts.add(paragraphText);
        }

        if (textParts.isEmpty()) {
            for (Map<String, Object> page : listOfMaps(analyzeResult.get("pages"))) {
                for (Map<String, Object> line : listOfMaps(page.get("lines"))) {
                    String lineText = String.valueOf(line.getOrDefault("content", "")).trim();
                    if (StringUtils.hasText(lineText)) {
                        textParts.add(lineText);
                    }
                }
            }
        }

        String text = normalize(String.join("\n\n", textParts));
        if (text.isBlank()) {
            throw new IllegalStateException("No extractable text found in " + downloadedBlob.fileName());
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("blob_uri", downloadedBlob.blobUri());
        metadata.put("source", downloadedBlob.fileName());
        metadata.put("page_number", spans.isEmpty() ? 1 : spans.get(0).page());
        metadata.put("di_processed", true);
        metadata.put("di_page_spans", spans);

        return new ParsedDocument(text, extractTitle(text, downloadedBlob.fileName()), metadata, spans);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private List<Double> numberList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::doubleValue)
                .toList();
    }

    private int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private boolean isDocumentIntelligenceEnabled() {
        return StringUtils.hasText(properties.documentIntelligence().endpoint())
                && StringUtils.hasText(properties.documentIntelligence().apiKey());
    }

    private boolean isDocumentIntelligenceSupported(String fileName) {
        String lowerFileName = fileName == null ? "" : fileName.toLowerCase();
        return DOCUMENT_INTELLIGENCE_EXTENSIONS.stream().anyMatch(lowerFileName::endsWith);
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

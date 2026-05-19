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
     * Used as the default parsing method or as a fallback for unsupported formats
     * and API failures.
     *
     * @param downloadedBlob the blob metadata and file reference to parse
     * @return the parsed document with extracted text and metadata
     */
    private ParsedDocument parseWithTika(DownloadedBlob downloadedBlob) {
        try (InputStream inputStream = Files.newInputStream(downloadedBlob.path())) {
            // Setup Tika content handler with unlimited characters (-1)
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata tikaMetadata = new Metadata();
            parser.parse(inputStream, handler, tikaMetadata, new ParseContext());

            // Normalize the extracted text to clean up excessive spacing/newlines
            String text = normalize(handler.toString());
            if (text.isBlank()) {
                throw new IllegalStateException("No extractable text found in " + downloadedBlob.fileName());
            }

            // Copy all Tika extracted metadata into standard map
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

    /**
     * Parses the document using Azure Document Intelligence REST API.
     * Sends the file as a base64 encoded string, initiates the analyze operation,
     * and polls the API for completion before parsing the result.
     *
     * @param downloadedBlob the blob reference containing the file path
     * @return the parsed document from Document Intelligence results
     * @throws Exception if API call fails, times out, or response parsing fails
     */
    private ParsedDocument parseWithDocumentIntelligence(DownloadedBlob downloadedBlob) throws Exception {
        // Build URL targeting the prebuilt-read model API
        String submitUrl = "%s/documentintelligence/documentModels/prebuilt-read:analyze?api-version=%s".formatted(
                CommonUtils.trimTrailingSlash(properties.documentIntelligence().endpoint()),
                properties.documentIntelligence().apiVersion()
        );
        // Read file contents and encode to Base64 format
        String base64Source = Base64.getEncoder().encodeToString(Files.readAllBytes(downloadedBlob.path()));

        // Submit the parsing job to Azure Document Intelligence
        ResponseEntity<Void> response = restClient.post()
                .uri(submitUrl)
                .header("Ocp-Apim-Subscription-Key", properties.documentIntelligence().apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("base64Source", base64Source))
                .retrieve()
                .toBodilessEntity();

        // Retrieve the poll endpoint URL from Operation-Location header
        String operationUrl = response.getHeaders().getFirst("Operation-Location");
        if (!StringUtils.hasText(operationUrl)) {
            throw new IllegalStateException("Document Intelligence did not return an Operation-Location header");
        }

        // Poll the job status until completion
        Map<String, Object> analyzeResult = pollDocumentIntelligenceOperation(operationUrl);
        return toParsedDocument(downloadedBlob, analyzeResult);
    }

    /**
     * Polls the Azure Document Intelligence operation endpoint until the task succeeds,
     * fails, or the overall wait time exceeds the deadline (120 seconds).
     *
     * @param operationUrl the URL to query the status of the analyze operation
     * @return the map representation of 'analyzeResult'
     * @throws InterruptedException if thread sleeping is interrupted
     * @throws IllegalStateException if operation fails or times out
     */
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
            
            // Check status of the asynchronous operation
            if ("succeeded".equalsIgnoreCase(status)) {
                Object analyzeResult = response.get("analyzeResult");
                return analyzeResult instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            }
            if ("failed".equalsIgnoreCase(status)) {
                throw new IllegalStateException("Document Intelligence operation failed: " + response.get("error"));
            }
            // Wait 3 seconds between successive polls to avoid rate limits
            Thread.sleep(3000);
        }
        throw new IllegalStateException("Document Intelligence operation timed out");
    }

    /**
     * Converts raw JSON structure from Azure Document Intelligence response to a structured ParsedDocument.
     * Extracts page layout and positional coordinates (spans) for paragraphs or lines.
     *
     * @param downloadedBlob the source blob data
     * @param analyzeResult raw response map from Azure API
     * @return structured parsed document
     */
    @SuppressWarnings("unchecked")
    private ParsedDocument toParsedDocument(DownloadedBlob downloadedBlob, Map<String, Object> analyzeResult) {
        List<Map<String, Object>> paragraphs = listOfMaps(analyzeResult.get("paragraphs"));
        List<DiSpan> spans = new ArrayList<>();
        List<String> textParts = new ArrayList<>();

        // Process paragraphs to extract content, page numbers, and bounding polygon coordinates
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

        // Fall back to extracting line by line if no paragraph data is available
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

        // Populate metadata for indexing
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("blob_uri", downloadedBlob.blobUri());
        metadata.put("source", downloadedBlob.fileName());
        metadata.put("page_number", spans.isEmpty() ? 1 : spans.get(0).page());
        metadata.put("di_processed", true);
        metadata.put("di_page_spans", spans);

        return new ParsedDocument(text, extractTitle(text, downloadedBlob.fileName()), metadata, spans);
    }

    /**
     * Safely casts an object to a List of Map<String, Object>.
     * Useful for parsing nested elements in response JSON.
     *
     * @param value raw object representing a list
     * @return typed List of Maps
     */
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

    /**
     * Safely casts and maps a generic list of numbers to a List of Doubles.
     * Used primarily for retrieving polygon coordinate values.
     *
     * @param value raw object representing a list of numbers
     * @return List of Double values
     */
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

    /**
     * Helper to safely extract integer value from numeric type, with fallback support.
     *
     * @param value numeric object
     * @param fallback default value if object is not a number
     * @return converted integer or fallback
     */
    private int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    /**
     * Checks if Azure Document Intelligence configuration endpoint and key are correctly populated.
     *
     * @return true if enabled, false otherwise
     */
    private boolean isDocumentIntelligenceEnabled() {
        return StringUtils.hasText(properties.documentIntelligence().endpoint())
                && StringUtils.hasText(properties.documentIntelligence().apiKey());
    }

    /**
     * Determines whether the given filename is supported natively by Azure Document Intelligence
     * based on its file extension.
     *
     * @param fileName name of the file to inspect
     * @return true if extension is supported by Document Intelligence
     */
    private boolean isDocumentIntelligenceSupported(String fileName) {
        String lowerFileName = fileName == null ? "" : fileName.toLowerCase();
        return DOCUMENT_INTELLIGENCE_EXTENSIONS.stream().anyMatch(lowerFileName::endsWith);
    }

    /**
     * Cleans up string line endings and replaces excessive newline sequences with standard format.
     *
     * @param text raw extracted text
     * @return normalized text
     */
    private String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
    }

    /**
     * Attempts to extract a meaningful title from the parsed content by identifying the first
     * non-empty line of substantial length. Falls back to filename if none found.
     *
     * @param text extracted document text
     * @param fallback the default title to return (usually the filename)
     * @return extracted title or fallback
     */
    private String extractTitle(String text, String fallback) {
        return text.lines()
                .map(String::trim)
                .filter(line -> line.length() > 10)
                .findFirst()
                .map(line -> line.substring(0, Math.min(line.length(), 100)))
                .orElse(fallback);
    }
}

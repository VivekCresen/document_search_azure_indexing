package com.cresensolutions.document_search_azure_indexing.parser;

import com.cresensolutions.document_search_azure_indexing.parser.Impl.DocumentParserServiceImpl;
import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.dto.DownloadedBlob;
import com.cresensolutions.document_search_azure_indexing.dto.ParsedDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class DocumentParserServiceTest {

    @Mock
    private AzureIndexingProperties properties;

    @Mock
    private AzureIndexingProperties.DocumentIntelligence documentIntelligence;

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    private DocumentParserService service;
    private Path tempFile;

    @BeforeEach
    void setUp() throws IOException {
        lenient().when(restClientBuilder.build()).thenReturn(restClient);
        lenient().when(properties.documentIntelligence()).thenReturn(documentIntelligence);
        
        service = new DocumentParserServiceImpl(properties, restClientBuilder);
        
        tempFile = Files.createTempFile("test-doc-", ".txt");
        Files.writeString(tempFile, "This is a line of test document content that is longer than ten characters.\n\nParagraph 2 text.");
    }

    @Test
    void testParseWithTikaSuccess() {
        lenient().when(documentIntelligence.endpoint()).thenReturn(""); // Disables DI

        DownloadedBlob blob = new DownloadedBlob("http://uri/file.txt", "file.txt", "file.txt", tempFile);

        ParsedDocument result = service.parse(blob);

        assertNotNull(result);
        assertTrue(result.text().contains("test document content"));
        assertEquals("This is a line of test document content that is longer than ten characters.", result.title());
        assertTrue(result.diSpans().isEmpty());
        assertEquals(false, result.metadata().get("di_processed"));
    }

    @Test
    void testParseWithTikaNoTextThrows() throws IOException {
        lenient().when(documentIntelligence.endpoint()).thenReturn(""); 
        Path emptyFile = Files.createTempFile("empty-", ".txt");

        DownloadedBlob blob = new DownloadedBlob("http://uri/file.txt", "file.txt", "file.txt", emptyFile);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.parse(blob));
        assertTrue(exception.getMessage().contains("Failed to parse") 
                || (exception.getCause() != null && exception.getCause().getMessage().contains("No extractable text found")));
    }

    @Test
    void testParseWithDocumentIntelligenceSuccessParagraphs() {
        lenient().when(documentIntelligence.endpoint()).thenReturn("https://di-endpoint/");
        lenient().when(documentIntelligence.apiKey()).thenReturn("di-key");
        lenient().when(documentIntelligence.apiVersion()).thenReturn("v1");

        DownloadedBlob blob = new DownloadedBlob("http://uri/file.pdf", "file.pdf", "file.pdf", tempFile);

        RestClient.RequestBodyUriSpec postUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec postSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        lenient().when(restClient.post()).thenReturn(postUriSpec);
        lenient().when(postUriSpec.uri(anyString())).thenReturn(postSpec);
        lenient().when(postSpec.header(anyString(), anyString())).thenReturn(postSpec);
        lenient().when(postSpec.contentType(any(MediaType.class))).thenReturn(postSpec);
        lenient().when(postSpec.body(any(Object.class))).thenReturn(postSpec);
        lenient().when(postSpec.retrieve()).thenReturn(responseSpec);

        ResponseEntity<Void> postResponse = mock(ResponseEntity.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Operation-Location", "http://polling-url");
        lenient().when(postResponse.getHeaders()).thenReturn(headers);
        lenient().when(responseSpec.toBodilessEntity()).thenReturn(postResponse);

        RestClient.RequestHeadersUriSpec getUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec getSpec = mock(RestClient.RequestHeadersSpec.class);
        lenient().when(restClient.get()).thenReturn(getUriSpec);
        lenient().when(getUriSpec.uri("http://polling-url")).thenReturn(getSpec);
        lenient().when(getSpec.header(anyString(), anyString())).thenReturn(getSpec);
        lenient().when(getSpec.retrieve()).thenReturn(responseSpec);

        Map<String, Object> runningResponse = Map.of("status", "running");
        
        Map<String, Object> spanRegion = Map.of(
                "pageNumber", 2,
                "polygon", List.of(1.0, 2.0, 3.0, 4.0)
        );
        Map<String, Object> paragraph1 = Map.of(
                "content", "Extracted Paragraph Content",
                "boundingRegions", List.of(spanRegion)
        );
        Map<String, Object> analyzeResult = Map.of(
                "paragraphs", List.of(paragraph1)
        );
        Map<String, Object> succeededResponse = Map.of(
                "status", "succeeded",
                "analyzeResult", analyzeResult
        );

        lenient().when(responseSpec.body(Map.class))
                .thenReturn(runningResponse)
                .thenReturn(succeededResponse);

        ParsedDocument result = service.parse(blob);

        assertNotNull(result);
        assertEquals("Extracted Paragraph Content", result.text());
        assertEquals("Extracted Paragraph Content", result.title());
        assertEquals(true, result.metadata().get("di_processed"));
        assertEquals(1, result.diSpans().size());
        assertEquals(2, result.diSpans().get(0).page());
        assertEquals(List.of(1.0, 2.0, 3.0, 4.0), result.diSpans().get(0).polygon());
    }

    @Test
    void testParseWithDocumentIntelligenceSuccessPagesLines() {
        lenient().when(documentIntelligence.endpoint()).thenReturn("https://di-endpoint/");
        lenient().when(documentIntelligence.apiKey()).thenReturn("di-key");
        lenient().when(documentIntelligence.apiVersion()).thenReturn("v1");

        DownloadedBlob blob = new DownloadedBlob("http://uri/file.pdf", "file.pdf", "file.pdf", tempFile);

        RestClient.RequestBodyUriSpec postUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec postSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        lenient().when(restClient.post()).thenReturn(postUriSpec);
        lenient().when(postUriSpec.uri(anyString())).thenReturn(postSpec);
        lenient().when(postSpec.header(anyString(), anyString())).thenReturn(postSpec);
        lenient().when(postSpec.contentType(any(MediaType.class))).thenReturn(postSpec);
        lenient().when(postSpec.body(any(Object.class))).thenReturn(postSpec);
        lenient().when(postSpec.retrieve()).thenReturn(responseSpec);

        ResponseEntity<Void> postResponse = mock(ResponseEntity.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Operation-Location", "http://polling-url");
        lenient().when(postResponse.getHeaders()).thenReturn(headers);
        lenient().when(responseSpec.toBodilessEntity()).thenReturn(postResponse);

        RestClient.RequestHeadersUriSpec getUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec getSpec = mock(RestClient.RequestHeadersSpec.class);
        lenient().when(restClient.get()).thenReturn(getUriSpec);
        lenient().when(getUriSpec.uri("http://polling-url")).thenReturn(getSpec);
        lenient().when(getSpec.header(anyString(), anyString())).thenReturn(getSpec);
        lenient().when(getSpec.retrieve()).thenReturn(responseSpec);

        Map<String, Object> line1 = Map.of("content", "Line one text");
        Map<String, Object> page1 = Map.of("lines", List.of(line1));
        Map<String, Object> analyzeResult = Map.of(
                "paragraphs", List.of(), // Trigger lines fallback
                "pages", List.of(page1)
        );
        Map<String, Object> succeededResponse = Map.of(
                "status", "succeeded",
                "analyzeResult", analyzeResult
        );

        lenient().when(responseSpec.body(Map.class)).thenReturn(succeededResponse);

        ParsedDocument result = service.parse(blob);

        assertNotNull(result);
        assertEquals("Line one text", result.text());
    }

    @Test
    void testParseWithDocumentIntelligenceNoHeadersFallbackToTika() {
        lenient().when(documentIntelligence.endpoint()).thenReturn("https://di-endpoint/");
        lenient().when(documentIntelligence.apiKey()).thenReturn("di-key");
        lenient().when(documentIntelligence.apiVersion()).thenReturn("v1");

        DownloadedBlob blob = new DownloadedBlob("http://uri/file.pdf", "file.pdf", "file.pdf", tempFile);

        RestClient.RequestBodyUriSpec postUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec postSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        lenient().when(restClient.post()).thenReturn(postUriSpec);
        lenient().when(postUriSpec.uri(anyString())).thenReturn(postSpec);
        lenient().when(postSpec.header(anyString(), anyString())).thenReturn(postSpec);
        lenient().when(postSpec.contentType(any(MediaType.class))).thenReturn(postSpec);
        lenient().when(postSpec.body(any(Object.class))).thenReturn(postSpec);
        lenient().when(postSpec.retrieve()).thenReturn(responseSpec);

        ResponseEntity<Void> postResponse = mock(ResponseEntity.class);
        lenient().when(postResponse.getHeaders()).thenReturn(new HttpHeaders());
        lenient().when(responseSpec.toBodilessEntity()).thenReturn(postResponse);

        ParsedDocument result = service.parse(blob);

        assertNotNull(result);
        assertEquals(false, result.metadata().get("di_processed"));
    }

    @Test
    void testParseWithDocumentIntelligenceFailedOperationFallbackToTika() {
        lenient().when(documentIntelligence.endpoint()).thenReturn("https://di-endpoint/");
        lenient().when(documentIntelligence.apiKey()).thenReturn("di-key");
        lenient().when(documentIntelligence.apiVersion()).thenReturn("v1");

        DownloadedBlob blob = new DownloadedBlob("http://uri/file.pdf", "file.pdf", "file.pdf", tempFile);

        RestClient.RequestBodyUriSpec postUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec postSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        lenient().when(restClient.post()).thenReturn(postUriSpec);
        lenient().when(postUriSpec.uri(anyString())).thenReturn(postSpec);
        lenient().when(postSpec.header(anyString(), anyString())).thenReturn(postSpec);
        lenient().when(postSpec.contentType(any(MediaType.class))).thenReturn(postSpec);
        lenient().when(postSpec.body(any(Object.class))).thenReturn(postSpec);
        lenient().when(postSpec.retrieve()).thenReturn(responseSpec);

        ResponseEntity<Void> postResponse = mock(ResponseEntity.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Operation-Location", "http://polling-url");
        lenient().when(postResponse.getHeaders()).thenReturn(headers);
        lenient().when(responseSpec.toBodilessEntity()).thenReturn(postResponse);

        RestClient.RequestHeadersUriSpec getUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec getSpec = mock(RestClient.RequestHeadersSpec.class);
        lenient().when(restClient.get()).thenReturn(getUriSpec);
        lenient().when(getUriSpec.uri("http://polling-url")).thenReturn(getSpec);
        lenient().when(getSpec.header(anyString(), anyString())).thenReturn(getSpec);
        lenient().when(getSpec.retrieve()).thenReturn(responseSpec);

        Map<String, Object> failedResponse = Map.of(
                "status", "failed",
                "error", "Invalid format"
        );
        lenient().when(responseSpec.body(Map.class)).thenReturn(failedResponse);

        ParsedDocument result = service.parse(blob);

        assertNotNull(result);
        assertEquals(false, result.metadata().get("di_processed"));
    }
}

package com.cresensolutions.document_search_azure_indexing.search;

import com.cresensolutions.document_search_azure_indexing.search.Impl.AzureSearchIndexServiceImpl;
import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AzureSearchIndexServiceTest {

    @Mock
    private AzureIndexingProperties properties;

    @Mock
    private AzureIndexingProperties.Search search;

    @Mock
    private AzureIndexingProperties.Settings settings;

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    private AzureSearchIndexService service;

    @BeforeEach
    void setUp() {
        lenient().when(restClientBuilder.build()).thenReturn(restClient);
        lenient().when(properties.search()).thenReturn(search);
        
        lenient().when(search.endpoint()).thenReturn("https://search-service/");
        lenient().when(search.indexName()).thenReturn("test-index");
        lenient().when(search.apiVersion()).thenReturn("2023-11-01");
        lenient().when(search.apiKey()).thenReturn("admin-key");

        service = new AzureSearchIndexServiceImpl(properties, restClientBuilder);
    }

    @Test
    void testCreateOrUpdateIndex_createsWhenMissing() {
        RestClient.RequestHeadersUriSpec<?> getUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> getSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec getResponseSpec = mock(RestClient.ResponseSpec.class);
        RestClient.RequestBodyUriSpec putUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec putSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec putResponseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) getUriSpec);
        when(getUriSpec.uri(anyString())).thenReturn((RestClient.RequestHeadersSpec) getSpec);
        when(getSpec.header(anyString(), anyString())).thenReturn((RestClient.RequestHeadersSpec) getSpec);
        when(getSpec.retrieve()).thenReturn(getResponseSpec);
        when(getResponseSpec.toBodilessEntity()).thenThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "Not Found",
                null,
                null,
                null
        ));

        when(restClient.put()).thenReturn(putUriSpec);
        when(putUriSpec.uri(anyString())).thenReturn(putSpec);
        when(putSpec.header(anyString(), anyString())).thenReturn(putSpec);
        when(putSpec.contentType(any(MediaType.class))).thenReturn(putSpec);
        when(putSpec.body(any(Object.class))).thenReturn(putSpec);
        when(putSpec.retrieve()).thenReturn(putResponseSpec);

        service.createOrUpdateIndex();

        verify(restClient).put();
        verify(putResponseSpec).toBodilessEntity();
    }

    @Test
    void testCreateOrUpdateIndex_skipsWhenExisting() {
        RestClient.RequestHeadersUriSpec<?> getUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> getSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec getResponseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) getUriSpec);
        when(getUriSpec.uri(anyString())).thenReturn((RestClient.RequestHeadersSpec) getSpec);
        when(getSpec.header(anyString(), anyString())).thenReturn((RestClient.RequestHeadersSpec) getSpec);
        when(getSpec.retrieve()).thenReturn(getResponseSpec);

        service.createOrUpdateIndex();

        verify(restClient, never()).put();
        verify(getResponseSpec).toBodilessEntity();
    }

    @Test
    void testUploadDocumentsEmpty() {
        service.uploadDocuments(null);
        service.uploadDocuments(List.of());
        verifyNoInteractions(restClient);
    }

    @Test
    void testUploadDocumentsWithBatching() {
        lenient().when(properties.settings()).thenReturn(settings);
        lenient().when(settings.batchSize()).thenReturn(2); // batch size 2, total 3 docs -> 2 batches

        RestClient.RequestBodyUriSpec postUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec postSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        lenient().when(restClient.post()).thenReturn(postUriSpec);
        lenient().when(postUriSpec.uri(anyString())).thenReturn(postSpec);
        lenient().when(postSpec.header(anyString(), anyString())).thenReturn(postSpec);
        lenient().when(postSpec.contentType(any(MediaType.class))).thenReturn(postSpec);
        lenient().when(postSpec.body(any(Object.class))).thenReturn(postSpec);
        lenient().when(postSpec.retrieve()).thenReturn(responseSpec);

        List<Map<String, Object>> docs = List.of(
                Map.of("id", "1", "content", "content1"),
                Map.of("id", "2", "content", "content2"),
                Map.of("id", "3", "content", "content3")
        );

        service.uploadDocuments(docs);

        verify(restClient, times(2)).post();
        verify(responseSpec, times(2)).toBodilessEntity();
    }

    @Test
    void testDeleteDocumentsByIdsEmpty() {
        service.deleteDocumentsByIds(null);
        service.deleteDocumentsByIds(List.of());
        verifyNoInteractions(restClient);
    }

    @Test
    void testDeleteDocumentsByIds() {
        lenient().when(properties.settings()).thenReturn(settings);
        lenient().when(settings.batchSize()).thenReturn(100);

        RestClient.RequestBodyUriSpec postUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec postSpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        lenient().when(restClient.post()).thenReturn(postUriSpec);
        lenient().when(postUriSpec.uri(anyString())).thenReturn(postSpec);
        lenient().when(postSpec.header(anyString(), anyString())).thenReturn(postSpec);
        lenient().when(postSpec.contentType(any(MediaType.class))).thenReturn(postSpec);
        lenient().when(postSpec.body(any(Object.class))).thenReturn(postSpec);
        lenient().when(postSpec.retrieve()).thenReturn(responseSpec);

        service.deleteDocumentsByIds(List.of("id1", "id2"));

        verify(restClient, times(1)).post();
    }
}

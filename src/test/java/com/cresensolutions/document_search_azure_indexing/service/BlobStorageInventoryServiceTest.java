package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.service.Impl.BlobStorageInventoryServiceImpl;
import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobItemProperties;
import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.dto.BlobInventoryItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlobStorageInventoryServiceTest {

    @Mock
    private BlobContainerClient containerClient;

    @Mock
    private AzureIndexingProperties properties;

    @Mock
    private AzureIndexingProperties.Storage storage;

    @Mock
    private AzureIndexingProperties.Settings settings;

    @InjectMocks
    private BlobStorageInventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(properties.storage()).thenReturn(storage);
        lenient().when(properties.settings()).thenReturn(settings);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListIndexableBlobs() {
        when(storage.directory()).thenReturn("folder/");
        when(settings.supportedExtensions()).thenReturn(List.of(".pdf", ".TXT"));

        BlobItem blob1 = mock(BlobItem.class);
        BlobItemProperties props1 = mock(BlobItemProperties.class);
        when(blob1.getName()).thenReturn("folder/file1.pdf");
        when(blob1.getProperties()).thenReturn(props1);
        when(props1.getLastModified()).thenReturn(OffsetDateTime.now());
        when(props1.getContentLength()).thenReturn(100L);
        when(props1.getETag()).thenReturn("etag1");

        // Should be filtered out due to prefix
        BlobItem blob2 = mock(BlobItem.class);
        when(blob2.getName()).thenReturn("otherfolder/file2.pdf");

        // Should be filtered out due to extension
        BlobItem blob3 = mock(BlobItem.class);
        when(blob3.getName()).thenReturn("folder/file3.png");

        // Should be filtered out because it contains "highlighted"
        BlobItem blob4 = mock(BlobItem.class);
        when(blob4.getName()).thenReturn("folder/file4_highlighted.pdf");

        // Valid text file
        BlobItem blob5 = mock(BlobItem.class);
        BlobItemProperties props5 = mock(BlobItemProperties.class);
        when(blob5.getName()).thenReturn("folder/file5.txt");
        when(blob5.getProperties()).thenReturn(props5);
        when(props5.getLastModified()).thenReturn(OffsetDateTime.now());
        when(props5.getContentLength()).thenReturn(200L);
        when(props5.getETag()).thenReturn("etag5");

        PagedIterable<BlobItem> pagedIterable = mock(PagedIterable.class);
        when(containerClient.listBlobs()).thenReturn(pagedIterable);
        when(pagedIterable.stream()).thenReturn(Stream.of(blob1, blob2, blob3, blob4, blob5));

        BlobClient blobClient1 = mock(BlobClient.class);
        BlobClient blobClient5 = mock(BlobClient.class);
        when(containerClient.getBlobClient("folder/file1.pdf")).thenReturn(blobClient1);
        when(containerClient.getBlobClient("folder/file5.txt")).thenReturn(blobClient5);
        when(blobClient1.getBlobUrl()).thenReturn("https://account.blob.core.windows.net/container/folder/file1.pdf");
        when(blobClient5.getBlobUrl()).thenReturn("https://account.blob.core.windows.net/container/folder/file5.txt");

        List<BlobInventoryItem> result = service.listIndexableBlobs();

        assertEquals(2, result.size());

        assertEquals("https://account.blob.core.windows.net/container/folder/file1.pdf", result.get(0).blobUri());
        assertEquals("folder/file1.pdf", result.get(0).blobName());
        assertEquals("file1.pdf", result.get(0).fileName());

        assertEquals("https://account.blob.core.windows.net/container/folder/file5.txt", result.get(1).blobUri());
        assertEquals("folder/file5.txt", result.get(1).blobName());
        assertEquals("file5.txt", result.get(1).fileName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testListIndexableBlobsWithEmptyPrefix() {
        when(storage.directory()).thenReturn("");
        when(settings.supportedExtensions()).thenReturn(List.of(".pdf"));

        BlobItem blob = mock(BlobItem.class);
        BlobItemProperties props = mock(BlobItemProperties.class);
        when(blob.getName()).thenReturn("anyfolder/file.pdf");
        when(blob.getProperties()).thenReturn(props);
        when(props.getLastModified()).thenReturn(OffsetDateTime.now());
        when(props.getContentLength()).thenReturn(50L);
        when(props.getETag()).thenReturn("etag");

        PagedIterable<BlobItem> pagedIterable = mock(PagedIterable.class);
        when(containerClient.listBlobs()).thenReturn(pagedIterable);
        when(pagedIterable.stream()).thenReturn(Stream.of(blob));

        BlobClient blobClient = mock(BlobClient.class);
        when(containerClient.getBlobClient("anyfolder/file.pdf")).thenReturn(blobClient);
        when(blobClient.getBlobUrl()).thenReturn("https://account.blob.core.windows.net/container/anyfolder/file.pdf");

        List<BlobInventoryItem> result = service.listIndexableBlobs();

        assertEquals(1, result.size());
        assertEquals("anyfolder/file.pdf", result.get(0).blobName());
    }
}

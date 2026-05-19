package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.service.Impl.BlobDownloadServiceImpl;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.cresensolutions.document_search_azure_indexing.dto.DownloadedBlob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlobDownloadServiceTest {

    @Mock
    private BlobContainerClient containerClient;

    @Mock
    private BlobClient blobClient;

    @InjectMocks
    private BlobDownloadServiceImpl service;

    @Test
    void testDownloadWithKnownNameAndFileName() {
        when(containerClient.getBlobClient("known-blob-name")).thenReturn(blobClient);

        DownloadedBlob result = service.download("http://uri/container/known-blob-name", "known-blob-name", "known-file-name");

        assertNotNull(result);
        assertEquals("known-blob-name", result.blobName());
        assertEquals("known-file-name", result.fileName());
        assertNotNull(result.path());
        assertTrue(result.path().toString().endsWith("known-file-name"));

        verify(blobClient).downloadToFile(anyString(), eq(true));

        // Cleanup
        service.cleanup(result);
        assertFalse(Files.exists(result.path()));
        assertFalse(Files.exists(result.path().getParent()));
    }

    @Test
    void testDownloadWithExtractedBlobNameAndFileName() {
        when(containerClient.getBlobContainerName()).thenReturn("mycontainer");
        when(containerClient.getBlobClient("folder/file.pdf")).thenReturn(blobClient);

        DownloadedBlob result = service.download("http://uri/mycontainer/folder/file.pdf", null, null);

        assertNotNull(result);
        assertEquals("folder/file.pdf", result.blobName());
        assertEquals("file.pdf", result.fileName());
        assertNotNull(result.path());

        verify(blobClient).downloadToFile(anyString(), eq(true));
        service.cleanup(result);
    }

    @Test
    void testDownloadWithExtractedBlobNameNoContainerInUri() {
        when(containerClient.getBlobContainerName()).thenReturn("mycontainer");
        when(containerClient.getBlobClient("file.pdf")).thenReturn(blobClient);

        DownloadedBlob result = service.download("http://uri/file.pdf", null, null);

        assertNotNull(result);
        assertEquals("file.pdf", result.blobName());
        assertEquals("file.pdf", result.fileName());
        assertNotNull(result.path());

        verify(blobClient).downloadToFile(anyString(), eq(true));
        service.cleanup(result);
    }

    @Test
    void testDownloadThrowsException() {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.createTempDirectory(anyString())).thenThrow(new IOException("Disk Full"));

            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                service.download("http://uri/mycontainer/file.pdf", "file.pdf", "file.pdf")
            );

            assertTrue(exception.getMessage().contains("Could not create temp file"));
        }
    }

    @Test
    void testCleanupNullBlobOrPath() {
        // Should not throw exceptions
        service.cleanup(null);
        service.cleanup(new DownloadedBlob("uri", "name", "file", null));
    }
}

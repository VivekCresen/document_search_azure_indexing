package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.service.Impl.FolderResolverServiceImpl;
import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FolderResolverServiceTest {

    @Mock
    private AzureIndexingProperties properties;

    @Mock
    private AzureIndexingProperties.Storage storage;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private FolderResolverServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(properties.storage()).thenReturn(storage);
        lenient().when(storage.containerName()).thenReturn("mycontainer");
    }

    @Test
    void testResolveFolderIdWithInvalidUri() {
        Optional<Long> result = service.resolveFolderId("invalid uri space");
        assertTrue(result.isEmpty());
    }

    @Test
    void testResolveFolderIdWithSegmentSizeLessThanTwo() {
        Optional<Long> result = service.resolveFolderId("http://localhost/mycontainer/file.pdf");
        assertTrue(result.isEmpty());
    }

    @Test
    void testResolveFolderIdSuccessfulHierarchy() {
        String uri = "http://localhost/mycontainer/folderA/folderB/123/file.pdf";

        // Segments extracted: [folderA, folderB, 123, file.pdf]
        // folderSegments: [folderA, folderB] (123 removed as it matches \d+)
        
        // Loop 1 (start = 0): subList(0, 2) => [folderA, folderB]
        // findFolder(folderA, null) -> returns 10L
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq("folderA")))
                .thenReturn(List.of(10L));
        // findFolder(folderB, 10L) -> returns 20L
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq("folderB"), eq(10L)))
                .thenReturn(List.of(20L));

        Optional<Long> result = service.resolveFolderId(uri);

        assertTrue(result.isPresent());
        assertEquals(20L, result.get());
    }

    @Test
    void testResolveFolderIdHierarchyFailsPartiallyThenResolvesFallbackByName() {
        String uri = "http://localhost/mycontainer/folderA/folderB/file.pdf";

        // folderSegments: [folderA, folderB]
        // start = 0: subList => [folderA, folderB]
        // findFolder(folderA, null) -> empty
        when(jdbcTemplate.queryForList(
                "select id from demo.documents where name = ? and is_file = false and parent_id is null limit 1",
                Long.class, "folderA"))
                .thenReturn(List.of());

        // start = 1: subList => [folderB]
        // findFolder(folderB, null) -> empty
        when(jdbcTemplate.queryForList(
                "select id from demo.documents where name = ? and is_file = false and parent_id is null limit 1",
                Long.class, "folderB"))
                .thenReturn(List.of());

        // Fallback by name: starts from folderSegments.size()-1 => index 1 (folderB)
        // findAnyFolderByName(folderB) -> returns 30L
        when(jdbcTemplate.queryForList(
                "select id from demo.documents where name = ? and is_file = false order by id desc limit 1",
                Long.class, "folderB"))
                .thenReturn(List.of(30L));

        Optional<Long> result = service.resolveFolderId(uri);

        assertTrue(result.isPresent());
        assertEquals(30L, result.get());
    }

    @Test
    void testResolveFolderIdHierarchyFailsFallbackFails() {
        String uri = "http://localhost/mycontainer/folderA/file.pdf";

        // folderSegments: [folderA]
        // findFolder(folderA, null) -> empty
        when(jdbcTemplate.queryForList(
                eq("select id from demo.documents where name = ? and is_file = false and parent_id is null limit 1"),
                eq(Long.class), eq("folderA")))
                .thenReturn(List.of());

        // findAnyFolderByName(folderA) -> empty
        when(jdbcTemplate.queryForList(
                eq("select id from demo.documents where name = ? and is_file = false order by id desc limit 1"),
                eq(Long.class), eq("folderA")))
                .thenReturn(List.of());

        Optional<Long> result = service.resolveFolderId(uri);

        assertTrue(result.isEmpty());
    }

    @Test
    void testExtractRelativePathSegmentsWithoutMarker() {
        // Test when URI path doesn't contain "/mycontainer/"
        String uri = "http://localhost/someotherpath/folderA/file.pdf";

        // folderSegments should be [someotherpath, folderA]
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq("someotherpath")))
                .thenReturn(List.of(99L));
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq("folderA"), eq(99L)))
                .thenReturn(List.of(100L));

        Optional<Long> result = service.resolveFolderId(uri);

        assertTrue(result.isPresent());
        assertEquals(100L, result.get());
    }
}

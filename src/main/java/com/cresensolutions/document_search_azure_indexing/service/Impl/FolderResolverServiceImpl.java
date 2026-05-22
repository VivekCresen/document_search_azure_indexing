package com.cresensolutions.document_search_azure_indexing.service.Impl;

import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import com.cresensolutions.document_search_azure_indexing.service.FolderResolverService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.cresensolutions.document_search_azure_indexing.commons.Constants;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link FolderResolverService} that uses Spring JdbcTemplate
 * to query virtual directory structures and caches resolved folder IDs to accelerate performance.
 */
@Service
@RequiredArgsConstructor
public class FolderResolverServiceImpl implements FolderResolverService {

    private final AzureIndexingProperties properties;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Resolves the parent folder ID corresponding to the given blob file storage path URI,
     * utilizing regional partition caches for high performance.
     *
     * @param blobUri the fully qualified storage URI of the document
     * @return an Optional containing the folder ID if found, or empty
     */
    @Override
    @Cacheable(value = Constants.CACHE_FOLDER_RESOLUTION, key = "#blobUri")
    public Optional<Long> resolveFolderId(String blobUri) {
        // Step 1: Decode and extract relative folder structure from the Blob URI
        List<String> segments = extractRelativePathSegments(blobUri);
        if (segments.size() <= 1) {
            return Optional.empty(); // No folder structure exists (e.g. root file)
        }

        // Target folders by removing the actual filename (last segment)
        List<String> folderSegments = new java.util.ArrayList<>(segments.subList(0, segments.size() - 1));
        // Prune off numeric ID segments if the last folder is just a database ID representation
        if (!folderSegments.isEmpty() && folderSegments.get(folderSegments.size() - 1).matches("\\d+")) {
            folderSegments.remove(folderSegments.size() - 1);
        }

        // Step 2: Try resolving folder hierarchy sequentially (from root or descending sub-paths)
        for (int start = 0; start < folderSegments.size(); start++) {
            Optional<Long> resolved = resolveHierarchy(folderSegments.subList(start, folderSegments.size()));
            if (resolved.isPresent()) {
                return resolved;
            }
        }

        // Step 3: Fallback - locate any matching folder by name in reverse order
        for (int index = folderSegments.size() - 1; index >= 0; index--) {
            Optional<Long> byName = findAnyFolderByName(folderSegments.get(index));
            if (byName.isPresent()) {
                return byName;
            }
        }

        return Optional.empty();
    }

    /**
     * Extracts and cleans relative path segments from the fully qualified blob storage URI.
     * Decodes URL character sequences, removes container prefixes, and splits folders by path separators.
     *
     * @param blobUri the storage reference URL to process
     * @return a list of folder names and filename segments
     */
    private List<String> extractRelativePathSegments(String blobUri) {
        try {
            // Replace space character representations and isolate path section
            String path = URI.create(blobUri.replace(" ", "%20")).getPath();
            String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8);
            String marker = "/" + properties.storage().containerName() + "/";
            // Trim standard container prefix to get the relative folder structure path
            String relativePath = decoded.contains(marker)
                    ? decoded.substring(decoded.indexOf(marker) + marker.length())
                    : decoded.replaceFirst("^/", "");
            return Arrays.stream(relativePath.split("/"))
                    .filter(segment -> !segment.isBlank())
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /**
     * Attempts to find a sequence of nested folder records in database following the segment hierarchy.
     * Starts with a parent_id of null (root folder) and traverses downwards.
     *
     * @param folderSegments ordered list of nested folder names
     * @return an Optional containing the terminal folder's database ID if resolved
     */
    private Optional<Long> resolveHierarchy(List<String> folderSegments) {
        Long parentId = null;
        Long currentId = null;
        for (String segment : folderSegments) {
            Optional<Long> next = findFolder(segment, parentId);
            if (next.isEmpty()) {
                return Optional.empty();
            }
            currentId = next.get();
            parentId = currentId; // Current folder becomes parent of next folder segment
        }
        return Optional.ofNullable(currentId);
    }

    /**
     * Queries database using JdbcTemplate to resolve a folder ID given its name and parent ID.
     * Handles root directories separately where parent_id is null.
     *
     * @param name folder name
     * @param parentId ID of the parent folder or null for root
     * @return an Optional containing the folder ID if found
     */
    private Optional<Long> findFolder(String name, Long parentId) {
        String sql = parentId == null
                ? "select id from prestage.documents where name = ? and is_file = false and parent_id is null limit 1"
                : "select id from prestage.documents where name = ? and is_file = false and parent_id = ? limit 1";
        List<Long> ids = parentId == null
                ? jdbcTemplate.queryForList(sql, Long.class, name)
                : jdbcTemplate.queryForList(sql, Long.class, name, parentId);
        return ids.stream().findFirst();
    }

    /**
     * Fallback query to find any folder record in the DB matching the name, sorting by ID desc.
     * Used when full path hierarchy resolution fails.
     *
     * @param name folder name
     * @return an Optional containing the latest matching folder ID if found
     */
    private Optional<Long> findAnyFolderByName(String name) {
        List<Long> ids = jdbcTemplate.queryForList(
                "select id from prestage.documents where name = ? and is_file = false order by id desc limit 1",
                Long.class,
                name
        );
        return ids.stream().findFirst();
    }
}

package com.cresensolutions.document_search_azure_indexing.service;

import com.cresensolutions.document_search_azure_indexing.config.AzureIndexingProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class FolderResolverService {

    private final AzureIndexingProperties properties;
    private final JdbcTemplate jdbcTemplate;

    public FolderResolverService(AzureIndexingProperties properties, JdbcTemplate jdbcTemplate) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Cacheable(value = "folderResolution", key = "#blobUri")
    public Optional<Long> resolveFolderId(String blobUri) {
        List<String> segments = extractRelativePathSegments(blobUri);
        if (segments.size() <= 1) {
            return Optional.empty();
        }

        List<String> folderSegments = new java.util.ArrayList<>(segments.subList(0, segments.size() - 1));
        if (!folderSegments.isEmpty() && folderSegments.get(folderSegments.size() - 1).matches("\\d+")) {
            folderSegments.remove(folderSegments.size() - 1);
        }

        for (int start = 0; start < folderSegments.size(); start++) {
            Optional<Long> resolved = resolveHierarchy(folderSegments.subList(start, folderSegments.size()));
            if (resolved.isPresent()) {
                return resolved;
            }
        }

        for (int index = folderSegments.size() - 1; index >= 0; index--) {
            Optional<Long> byName = findAnyFolderByName(folderSegments.get(index));
            if (byName.isPresent()) {
                return byName;
            }
        }

        return Optional.empty();
    }

    private List<String> extractRelativePathSegments(String blobUri) {
        try {
            String path = URI.create(blobUri.replace(" ", "%20")).getPath();
            String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8);
            String marker = "/" + properties.storage().containerName() + "/";
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

    private Optional<Long> resolveHierarchy(List<String> folderSegments) {
        Long parentId = null;
        Long currentId = null;
        for (String segment : folderSegments) {
            Optional<Long> next = findFolder(segment, parentId);
            if (next.isEmpty()) {
                return Optional.empty();
            }
            currentId = next.get();
            parentId = currentId;
        }
        return Optional.ofNullable(currentId);
    }

    private Optional<Long> findFolder(String name, Long parentId) {
        String sql = parentId == null
                ? "select id from prestage.documents where name = ? and is_file = false and parent_id is null limit 1"
                : "select id from prestage.documents where name = ? and is_file = false and parent_id = ? limit 1";
        List<Long> ids = parentId == null
                ? jdbcTemplate.queryForList(sql, Long.class, name)
                : jdbcTemplate.queryForList(sql, Long.class, name, parentId);
        return ids.stream().findFirst();
    }

    private Optional<Long> findAnyFolderByName(String name) {
        List<Long> ids = jdbcTemplate.queryForList(
                "select id from prestage.documents where name = ? and is_file = false order by id desc limit 1",
                Long.class,
                name
        );
        return ids.stream().findFirst();
    }
}

package com.cresensolutions.document_search_azure_indexing.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.cresensolutions.document_search_azure_indexing.commons.Constants;

import java.util.concurrent.TimeUnit;

/**
 * Configuration class enabling Spring's declarative annotation-driven caching,
 * leveraging Caffeine as the underlying in-memory cache manager implementation.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Registers a Caffeine-backed CacheManager specifically to store resolved folder path mappings.
     *
     * @return cache manager bean instance
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(Constants.CACHE_FOLDER_RESOLUTION);
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }

    Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(500)
                .expireAfterAccess(60, TimeUnit.MINUTES)
                .recordStats();
    }
}

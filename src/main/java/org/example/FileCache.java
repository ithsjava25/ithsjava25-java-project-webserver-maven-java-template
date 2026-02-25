package org.example;

import java.io.IOException;

/**
 * Interface for pluggable file caching implementations.
 * Allows for different cache backends (in-memory, Redis, Caffeine, etc.)
 */
public interface FileCache {
    /**
     * Retrieves cached file or fetches from provider if not cached.
     * Implementation must be thread-safe.
     *
     * @param cacheKey Unique cache key (typically includes webRoot + uri)
     * @param provider Function to fetch file bytes if not cached
     * @return File bytes, or null if file not found
     * @throws IOException if fetch fails
     */
    byte[] getOrFetch(String cacheKey, FileProvider provider) throws IOException;

    /**
     * Clear all cached entries.
     */
    void clearCache();

    /**
     * Get cache statistics.
     */
    CacheStats getStats();

    /**
     * Functional interface for file fetching providers.
     */
    @FunctionalInterface
    interface FileProvider {
        byte[] fetch(String uri) throws IOException;
    }

    /**
     * Cache statistics record.
     */
    class CacheStats {
        public final int entries;
        public final long bytes;
        public final int maxEntries;
        public final long maxBytes;
        public final long totalAccesses;

        public CacheStats(int entries, long bytes, int maxEntries, long maxBytes, long totalAccesses) {
            this.entries = entries;
            this.bytes = bytes;
            this.maxEntries = maxEntries;
            this.maxBytes = maxBytes;
            this.totalAccesses = totalAccesses;
        }

        @Override
        public String toString() {
            return String.format(
                    "CacheStats{entries=%d/%d, bytes=%d/%d, utilization=%.1f%%, accesses=%d}",
                    entries, maxEntries, bytes, maxBytes,
                    (double) bytes / maxBytes * 100, totalAccesses
            );
        }
    }
}

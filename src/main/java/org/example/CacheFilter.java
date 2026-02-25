
package org.example;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe cache filter using ConcurrentHashMap
 * Handles caching with LRU eviction for large files
 */
public class CacheFilter {
    private static final int MAX_CACHE_ENTRIES = 100;
    private static final long MAX_CACHE_BYTES = 50 * 1024 * 1024;// 50MB

    // Lock-free concurrent cache
    private final ConcurrentHashMap<String, CacheEntry> cache =
            new ConcurrentHashMap<>(16, 0.75f, 16);

    private final AtomicLong currentBytes = new AtomicLong(0);
    private final Object writeLock = new Object(); // För atomära operationer

    /**
     * Cache entry med metadata för LRU tracking
     */
    private static class CacheEntry {
        final byte[] data;
        final AtomicLong lastAccessTime;
        final AtomicLong accessCount;

        CacheEntry(byte[] data) {
            this.data = data;
            this.lastAccessTime = new AtomicLong(System.currentTimeMillis());
            this.accessCount = new AtomicLong(0);
        }

        void recordAccess() {
            accessCount.incrementAndGet();
            lastAccessTime.set(System.currentTimeMillis());
        }
    }

    /**
     * Hämta från cache eller fetch från provider (thread-safe)
     * Använder double-checked locking för att undvika TOCTOU-race
     */
    public byte[] getOrFetch(String uri, FileProvider provider) throws IOException {
        // First check - lock-free read (snabb väg)
        CacheEntry entry = cache.get(uri);
        if (entry != null) {
            entry.recordAccess();
            System.out.println("✓ Cache hit for: " + uri);
            return entry.data;
        }

        // Cache miss - fetch från provider under lock
        synchronized (writeLock) {
            // Second check - verifierar att ingen annan tråd fetchade medan vi väntade på lock
            entry = cache.get(uri);
            if (entry != null) {
                entry.recordAccess();
                System.out.println("✓ Cache hit for: " + uri + " (from concurrent fetch)");
                return entry.data;
            }

            // Fetch och cachelagra
            System.out.println("✗ Cache miss for: " + uri);
            byte[] fileBytes = provider.fetch(uri);

            if (fileBytes != null) {
                addToCacheUnsafe(uri, fileBytes);
            }

            return fileBytes;
        }
    }

    /**
     * Lägg till i cache med eviction om nödvändigt (MÅSTE VARA UNDER LOCK)
     */
    private void addToCacheUnsafe(String uri, byte[] data) {
        // Guard mot oversized entries som kan blockera eviction
        if (data.length > MAX_CACHE_BYTES) {
            System.out.println("⚠️ Skipping cache for oversized file: " + uri +
                    " (" + (data.length / 1024 / 1024) + "MB > " +
                    (MAX_CACHE_BYTES / 1024 / 1024) + "MB)");
            return;
        }

        // Evicta medan nödvändigt (med empty-check för infinite loop)
        while (shouldEvict(data) && !cache.isEmpty()) {
            evictLeastRecentlyUsedUnsafe();
        }

        // Om cache fortfarande är full efter eviction, hoppa över
        if (shouldEvict(data)) {
            System.out.println("⚠️ Cache full, skipping: " + uri);
            return;
        }

        CacheEntry newEntry = new CacheEntry(data);
        CacheEntry oldEntry = cache.put(uri, newEntry);

        // Uppdatera byte-count
        if (oldEntry != null) {
            currentBytes.addAndGet(-oldEntry.data.length);
        }
        currentBytes.addAndGet(data.length);
    }

    /**
     * Kontrollera om vi behöver evicta
     */
    private boolean shouldEvict(byte[] newValue) {
        return cache.size() >= MAX_CACHE_ENTRIES ||
                (currentBytes.get() + newValue.length) > MAX_CACHE_BYTES;
    }

    /**
     * Evicta minst nyligen använd entry (MÅSTE VARA UNDER LOCK)
     */
    private void evictLeastRecentlyUsedUnsafe() {
        if (cache.isEmpty()) return;

        // Hitta entry med minst senaste access
        String keyToRemove = cache.entrySet().stream()
                .min((a, b) -> Long.compare(
                        a.getValue().lastAccessTime.get(),
                        b.getValue().lastAccessTime.get()
                ))
                .map(java.util.Map.Entry::getKey)
                .orElse(null);

        if (keyToRemove != null) {
            CacheEntry removed = cache.remove(keyToRemove);
            if (removed != null) {
                currentBytes.addAndGet(-removed.data.length);
                System.out.println("✗ Evicted from cache: " + keyToRemove +
                        " (accesses: " + removed.accessCount.get() + ")");
            }
        }
    }

    /**
     * Rensa cache atomärt
     */
    public void clearCache() {
        synchronized (writeLock) {
            cache.clear();
            currentBytes.set(0);
        }
    }

    public CacheStats getStats() {
        long totalAccesses = cache.values().stream()
                .mapToLong(e -> e.accessCount.get())
                .sum();

        return new CacheStats(
                cache.size(),
                currentBytes.get(),
                MAX_CACHE_ENTRIES,
                MAX_CACHE_BYTES,
                totalAccesses
        );
    }

    // Stats-klass
    public static class CacheStats {
        public final int entries;
        public final long bytes;
        public final int maxEntries;
        public final long maxBytes;
        public final long totalAccesses;

        CacheStats(int entries, long bytes, int maxEntries, long maxBytes, long totalAccesses) {
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

    @FunctionalInterface
    public interface FileProvider {
        byte[] fetch(String uri) throws IOException;
    }
}

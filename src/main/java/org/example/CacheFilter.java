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
        new ConcurrentHashMap<>(16, 0.75f, 16); // 16 segments för concurrency
    
    private final AtomicLong currentBytes = new AtomicLong(0);

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
     */
    public byte[] getOrFetch(String uri, FileProvider provider) throws IOException {
        // Kontrollera cache (lock-free read)
        if (cache.containsKey(uri)) {
            CacheEntry entry = cache.get(uri);
            if (entry != null) {
                entry.recordAccess();
                System.out.println(" Cache hit for: " + uri);
                return entry.data;
            }
        }

        // Cache miss - fetch från provider
        System.out.println(" Cache miss for: " + uri);
        byte[] fileBytes = provider.fetch(uri);
        
        // Lägg till i cache
        addToCache(uri, fileBytes);
        
        return fileBytes;
    }

    /**
     * Lägg till i cache med eviction om nödvändigt (thread-safe)
     */
    private synchronized void addToCache(String uri, byte[] data) {
        // Kontrollera om vi måste evicta innan vi lägger till
        while (shouldEvict(data)) {
            evictLeastRecentlyUsed();
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
     * Evicta minst nyligen använd entry
     */
    private void evictLeastRecentlyUsed() {
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

    // Diagnostik-metoder
    public int getCacheSize() {
        return cache.size();
    }

    public long getCurrentBytes() {
        return currentBytes.get();
    }

    public long getMaxBytes() {
        return MAX_CACHE_BYTES;
    }

    public double getCacheUtilization() {
        return (double) currentBytes.get() / MAX_CACHE_BYTES * 100;
    }

    public void clearCache() {
        cache.clear();
        currentBytes.set(0);
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

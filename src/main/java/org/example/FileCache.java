package org.example;

import java.util.concurrent.ConcurrentHashMap;

public class FileCache {
    private static final ConcurrentHashMap<String, byte[]> cache = new ConcurrentHashMap<>();

    private FileCache() {}

    public static byte[] get(String key) {
        return cache.get(key);
    }

    public static void put(String key, byte[] content) {
        cache.put(key, content);
    }

    public static void clear() {
        cache.clear();
    }
}


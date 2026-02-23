package org.example;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileCache {
    private final ConcurrentHashMap<String, byte []> cache = new ConcurrentHashMap<>();

    public boolean contains (String key) {
        return cache.containsKey(key);
    }

    public byte[] get(String key) {
        return cache.get(key);
    }

    public void put(String key, byte[] value){
        cache.put(key, value);
    }
    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

}

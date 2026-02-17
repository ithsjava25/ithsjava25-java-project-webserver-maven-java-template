package org.example;

import java.util.HashMap;
import java.util.Map;

public class FileCache {
    private final Map<String, byte []> cache = new HashMap<>();

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

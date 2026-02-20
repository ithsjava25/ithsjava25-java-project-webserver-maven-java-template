package org.example;

import org.example.http.HttpResponseBuilder;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Map;

public class StaticFileHandler {
    private static final String WEB_ROOT = "www";
    private final CacheFilter cacheFilter = new CacheFilter();

    public void sendGetRequest(OutputStream outputStream, String uri) throws IOException {
        byte[] fileBytes = cacheFilter.getOrFetch(uri, 
            path -> Files.readAllBytes(new File(WEB_ROOT, path).toPath())
        );
        
        HttpResponseBuilder response = new HttpResponseBuilder();
        response.setHeaders(Map.of("Content-Type", "text/html; charset=utf-8"));
        response.setBody(fileBytes);
        PrintWriter writer = new PrintWriter(outputStream, true);
        writer.println(response.build());
    }
}

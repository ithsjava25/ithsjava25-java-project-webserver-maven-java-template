package org.example;

import org.example.http.HttpResponseBuilder;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class StaticFileHandler {
    private static final String DEFAULT_WEB_ROOT = "www";
    
    // ✅ EN shared cache för alla threads
    private static final CacheFilter SHARED_CACHE = new CacheFilter();
    
    private final String webRoot;

    public StaticFileHandler() {
        this(DEFAULT_WEB_ROOT);
    }


    public StaticFileHandler(String webRoot) {
        this.webRoot = webRoot;
    }

    public void sendGetRequest(OutputStream outputStream, String uri) throws IOException {
        try {
            String sanitizedUri = sanitizeUri(uri);
            
            if (isPathTraversal(sanitizedUri)) {
                sendErrorResponse(outputStream, 403, "Forbidden");
                return;
            }
            
            // Använd shared cache istället för ny instans
            byte[] fileBytes = SHARED_CACHE.getOrFetch(sanitizedUri,
                path -> Files.readAllBytes(new File(webRoot, path).toPath())
            );
            
            HttpResponseBuilder response = new HttpResponseBuilder();
            response.setHeaders(Map.of("Content-Type", "text/html; charset=UTF-8"));
            response.setBody(fileBytes);
            outputStream.write(response.build());
            outputStream.flush();
            
        } catch (IOException e) {
            try {
                sendErrorResponse(outputStream, 404, "Not Found");
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private String sanitizeUri(String uri) {
        uri = uri.split("\\?")[0];
        uri = uri.split("#")[0];
        uri = uri.replace("\0", "");
        
        while (uri.startsWith("/")) {
            uri = uri.substring(1);
        }
        
        return uri;
    }

    private boolean isPathTraversal(String uri) {
        try {
            Path webRootPath = Paths.get(webRoot).toRealPath();
            Path requestedPath = webRootPath.resolve(uri).normalize();
            
            return !requestedPath.startsWith(webRootPath);
        } catch (IOException e) {
            return true;
        }
    }

    private void sendErrorResponse(OutputStream outputStream, int statusCode, String statusMessage) throws IOException {
        HttpResponseBuilder response = new HttpResponseBuilder();
        response.setStatusCode(statusCode);
        response.setHeaders(Map.of("Content-Type", "text/html; charset=UTF-8"));
        String body = "<html><body><h1>" + statusCode + " " + statusMessage + "</h1></body></html>";
        response.setBody(body);
        outputStream.write(response.build());
        outputStream.flush();
    }

    //Diagnostik-metod
    public static CacheFilter.CacheStats getCacheStats() {
        return SHARED_CACHE.getStats();
    }

    public static void clearCache() {
        SHARED_CACHE.clearCache();
    }
}

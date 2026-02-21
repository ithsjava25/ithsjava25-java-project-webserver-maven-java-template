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
    private final String webRoot;
    private final CacheFilter cacheFilter;

    // Standardkonstruktor - använder "www"
    public StaticFileHandler() {
        this(DEFAULT_WEB_ROOT);
    }

    // Konstruktor som tar en anpassad webRoot-sökväg (för tester)
    public StaticFileHandler(String webRoot) {
        this.webRoot = webRoot;
        this.cacheFilter = new CacheFilter();
    }

    public void sendGetRequest(OutputStream outputStream, String uri) throws IOException {
        try {
            // Sanera URI: ta bort frågetecken, hashtaggar, ledande snedstreck och null-bytes
            String sanitizedUri = sanitizeUri(uri);
            
            // Kontrollera för sökvägsgenomgång-attacker med Path normalisering
            if (isPathTraversal(sanitizedUri)) {
                sendErrorResponse(outputStream, 403, "Forbidden");
                return;
            }
            
            byte[] fileBytes = cacheFilter.getOrFetch(sanitizedUri,
                path -> Files.readAllBytes(new File(webRoot, path).toPath())
            );
            
            HttpResponseBuilder response = new HttpResponseBuilder();
            response.setHeaders(Map.of("Content-Type", "text/html; charset=UTF-8"));
            response.setBody(fileBytes);
            outputStream.write(response.build());
            outputStream.flush();
            
        } catch (IOException e) {
            // Hantera saknad fil och andra IO-fel
            try {
                sendErrorResponse(outputStream, 404, "Not Found");
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private String sanitizeUri(String uri) {
        // Ta bort frågesträngar (?)
        uri = uri.split("\\?")[0];
        
        // Ta bort fragment (#)
        uri = uri.split("#")[0];
        
        // Ta bort null-bytes
        uri = uri.replace("\0", "");
        
        // Ta bort ledande snedstreck
        while (uri.startsWith("/")) {
            uri = uri.substring(1);
        }
        
        return uri;
    }

    private boolean isPathTraversal(String uri) {
        // Kontrollera för kataloggenomgång-försök
        try {
            // Normalisera sökvägen för att detektera traversal-försök
            Path webRootPath = Paths.get(webRoot).toRealPath();
            Path requestedPath = webRootPath.resolve(uri).normalize();
            
            // Om den normaliserade sökvägen är utanför webRoot, är det path traversal
            return !requestedPath.startsWith(webRootPath);
        } catch (IOException e) {
            // Om något går fel vid normalisering, behandla det som potentiell path traversal
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
}

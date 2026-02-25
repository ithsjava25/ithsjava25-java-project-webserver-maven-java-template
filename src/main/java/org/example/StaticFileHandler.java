package org.example;

import org.example.http.HttpResponseBuilder;
import static org.example.http.HttpResponseBuilder.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

public class StaticFileHandler {
    private static final String DEFAULT_WEB_ROOT = "www";
    private static final Logger LOGGER = Logger.getLogger(StaticFileHandler.class.getName());
    
    // EN shared cache för alla threads
    private static final FileCache SHARED_CACHE = new CacheFilter();
    
    private final String webRoot;

    public StaticFileHandler() {
        this(DEFAULT_WEB_ROOT);
    }


    public StaticFileHandler(String webRoot) {
        this.webRoot = webRoot;
    }

    public void sendGetRequest(OutputStream outputStream, String uri) throws IOException {
        String sanitizedUri = sanitizeUri(uri);

        if (isPathTraversal(sanitizedUri)) {
            sendErrorResponse(outputStream, 403, "Forbidden");
            return;
        }

        try {
            // Cache-nyckel inkluderar nu webRoot för att undvika collisions
            String cacheKey = generateCacheKey(sanitizedUri);
            byte[] fileBytes = SHARED_CACHE.getOrFetch(cacheKey,
                    ignoredPath -> Files.readAllBytes(new File(webRoot, sanitizedUri).toPath())
            );


            HttpResponseBuilder response = new HttpResponseBuilder();
            response.setContentTypeFromFilename(sanitizedUri);
            response.setBody(fileBytes);
            outputStream.write(response.build());
            outputStream.flush();

        } catch (IOException e) {
            LOGGER.log(Level.FINE, "File not found or read error: " + uri, e);
            sendErrorResponse(outputStream, 404, "Not Found");
        }
    }

    /**
     * Generates a unique cache key that includes webRoot to prevent collisions
     * between different handler instances
     */
    private String generateCacheKey(String sanitizedUri) {
        return webRoot + ":" + sanitizedUri;
    }

    /**
     * Sanitizes URI by removing query strings, fragments, null bytes, and leading slashes.
     * Also performs URL-decoding to normalize percent-encoded sequences.
     */
    private String sanitizeUri(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "index.html";
        }

        // Ta bort query string och fragment
        int queryIndex = uri.indexOf('?');
        int fragmentIndex = uri.indexOf('#');
        int endIndex = Math.min(
                queryIndex > 0 ? queryIndex : uri.length(),
                fragmentIndex > 0 ? fragmentIndex : uri.length()
        );

        uri = uri.substring(0, endIndex)
                .replace("\0", "")
                .replaceAll("^/+", "");  // Bort med leading slashes

        // URL-decode för att normalisera percent-encoded sequences (t.ex. %2e%2e -> ..)
        try {
            uri = URLDecoder.decode(uri, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Ogiltig URL-kodning i URI: " + uri);
            // Returna som den är om avkodning misslyckas; isPathTraversal kommer hantera det
        }

        return uri.isEmpty() ? "index.html" : uri;
    }

    /**
     * Kontrollerar om den begärda sökvägen försöker traversera utanför webroten.
     * Använder sökvägsnormalisering efter avkodning för att fånga traversalförsök.
     */
    private boolean isPathTraversal(String uri) {
        try {
            // Använd absolutsökväg + normalisera istället för toRealPath() för att undvika
            // krav på att katalogen existerar och för att hantera symboliska länkar säkert
            Path webRootPath = Paths.get(webRoot).toAbsolutePath().normalize();
            Path requestedPath = webRootPath.resolve(uri).normalize();

            // Returnera true om den begärda sökvägen inte ligger under webroten
            return !requestedPath.startsWith(webRootPath);
        } catch (Exception e) {
            // Om något går fel under sökvägsvalideringen, tillåt inte åtkomst (säker utgång)
            LOGGER.log(Level.WARNING, "Sökvägstraversalkontroll misslyckades för: " + uri, e);
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
    public static FileCache.CacheStats getCacheStats() {
        FileCache.CacheStats stats = SHARED_CACHE.getStats();
        return stats != null ? stats : new FileCache.CacheStats();
    }

    public static void clearCache() {
        SHARED_CACHE.clearCache();
    }
}


package org.example;

import org.example.http.HttpResponseBuilder;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.NoSuchFileException;


public class StaticFileHandler {
    private static final String DEFAULT_WEB_ROOT = "www";
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
            writeResponse(outputStream, 403, "Forbidden");
            return;
        }

        try {
            String cacheKey = "www:" + sanitizedUri;
            byte[] fileBytes = FileCache.get(cacheKey);

            if (fileBytes == null) {
                fileBytes = Files.readAllBytes(new File(webRoot, sanitizedUri).toPath());
                FileCache.put(cacheKey, fileBytes);
            }

            HttpResponseBuilder response = new HttpResponseBuilder();
            response.setContentTypeFromFilename(sanitizedUri);
            response.setBody(fileBytes);
            outputStream.write(response.build());
            outputStream.flush();

        } catch (NoSuchFileException e) {
            writeResponse(outputStream, 404, "Not Found");
        } catch (IOException e) {
            writeResponse(outputStream, 500, "Internal Server Error");
        }
    }


    private String sanitizeUri(String uri) {
        if (uri == null || uri.isEmpty()) return "index.html";

        int endIndex = Math.min(
                uri.indexOf('?') < 0 ? uri.length() : uri.indexOf('?'),
                uri.indexOf('#') < 0 ? uri.length() : uri.indexOf('#')
        );

        return uri.substring(0, endIndex)
                .replace("\0", "")
                .replaceAll("^/+", "")
                .replaceAll("^$", "index.html");
    }

    private boolean isPathTraversal(String uri) {
        try {
            Path webRootPath = Paths.get(webRoot).toAbsolutePath().normalize();
            Path requestedPath = webRootPath.resolve(uri).normalize();
            return !requestedPath.startsWith(webRootPath);
        } catch (Exception e) {
            return true;
        }
    }

    private void writeResponse(OutputStream outputStream, int statusCode, String statusMessage) throws IOException {
        HttpResponseBuilder response = new HttpResponseBuilder();
        response.setStatusCode(statusCode);
        response.setHeader("Content-Type", "text/html; charset=UTF-8");
        response.setBody(String.format("<html><body><h1>%d %s</h1></body></html>", statusCode, statusMessage));
        outputStream.write(response.build());
        outputStream.flush();
    }

    public static void clearCache() {
        FileCache.clear();
    }
}

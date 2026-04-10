package org.example;

import org.example.http.HttpResponseBuilder;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

import static org.example.http.HttpResponseBuilder.*;

public class StaticFileHandler {

    private final String WEB_ROOT;
    private byte[] fileBytes;
    private int statusCode;

    public static final String TEXT_PLAIN_CHARSET_UTF_8 = "text/plain; charset=utf-8";

    public StaticFileHandler() {
        WEB_ROOT = "www";
    }

    public StaticFileHandler(String webRoot) {
        this.WEB_ROOT = webRoot;
    }

    private void handleGetRequest(String uri) throws IOException {
        // Clean URI
        int q = uri.indexOf('?');
        if (q >= 0) uri = uri.substring(0, q);
        int h = uri.indexOf('#');
        if (h >= 0) uri = uri.substring(0, h);
        uri = uri.replace("\0", "");
        if (uri.startsWith("/")) uri = uri.substring(1);
        if (uri.isEmpty()) uri = "index.html";

        File root = new File(WEB_ROOT).getCanonicalFile();
        File file = new File(root, uri).getCanonicalFile();

        if (!file.toPath().startsWith(root.toPath())) {
            fileBytes = "403 Forbidden".getBytes();
            statusCode = SC_FORBIDDEN;
            return;
        }

        if (file.isFile()) {
            fileBytes = Files.readAllBytes(file.toPath());
            statusCode = SC_OK;
        } else {
            File errorFile = new File(WEB_ROOT, "pageNotFound.html");
            if (errorFile.isFile()) {
                fileBytes = Files.readAllBytes(errorFile.toPath());
            } else {
                fileBytes = "404 Not Found".getBytes();
            }
            statusCode = SC_NOT_FOUND;
        }
    }

    public void sendGetRequest(OutputStream outputStream, String uri) throws IOException {
        handleGetRequest(uri);
        HttpResponseBuilder response = new HttpResponseBuilder();
        response.setStatusCode(statusCode);
        response.setContentTypeFromFilename(uri);
        response.setBody(fileBytes);
        outputStream.write(response.build());
        outputStream.flush();
    }

    public void sendHeadRequest(OutputStream outputStream, String uri) throws IOException {
        handleGetRequest(uri);
        HttpResponseBuilder response = new HttpResponseBuilder();
        response.setStatusCode(statusCode);
        response.setContentTypeFromFilename(uri);
        response.setHeader("Content-Length", String.valueOf(fileBytes.length));
        response.setBody(new byte[0]);
        outputStream.write(response.build());
        outputStream.flush();
    }
}

package org.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;

public class StaticFileHttpHandler implements HttpHandler {

    private final StaticFileHandler handler;

    public StaticFileHttpHandler() {
        this.handler = new StaticFileHandler();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        String path = exchange.getRequestURI().getPath();
        byte[] fileBytes;
        int statusCode;

        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {

            try {
                handler.handleGetRequest(path);
                fileBytes = handler.getFileBytes();
                statusCode = handler.getStatusCode();
            } catch (IOException e) {
                fileBytes = "500 Internal Server Error".getBytes();
                statusCode = 500;
            }

        } else {
            fileBytes = "405 Method Not Allowed".getBytes();
            statusCode = 405;
        }

        exchange.sendResponseHeaders(statusCode, fileBytes.length);
        exchange.getResponseBody().write(fileBytes);
        exchange.getResponseBody().close();
    }
}
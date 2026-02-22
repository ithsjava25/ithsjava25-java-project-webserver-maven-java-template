package org.example;

import org.example.httpparser.HttpParser;

import java.io.IOException;
import java.net.Socket;

public class ConnectionHandler implements AutoCloseable {

    Socket client;
    String uri;

    public ConnectionHandler(Socket client) {
        this.client = client;
    }

    public void runConnectionHandler() throws IOException {
        StaticFileHandler sfh = new StaticFileHandler();
        HttpParser parser = new HttpParser();
        parser.setReader(client.getInputStream());
        parser.parseRequest();
        parser.parseHttp();

        // --- DIN ÄNDRING FÖR ISSUE #75 BÖRJAR HÄR ---
        String requestedUri = parser.getUri();
        if (requestedUri.equals("/health")) {
            String responseBody = "{\"status\": \"ok\"}";
            String header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: " + responseBody.length() + "\r\n" +
                    "\r\n";

            client.getOutputStream().write(header.getBytes());
            client.getOutputStream().write(responseBody.getBytes());
            client.getOutputStream().flush();
            return; // Avslutar här så vi inte letar efter filer i onödan
        }
        // --- DIN ÄNDRING SLUTAR HÄR ---

        resolveTargetFile(requestedUri);
        sfh.sendGetRequest(client.getOutputStream(), uri);
    }

    private void resolveTargetFile(String uri) {
        if (uri.matches("/$")) { //matches(/)
            this.uri = "index.html";
        } else if (uri.matches("^(?!.*\\.html$).*$")) {
            this.uri = uri.concat(".html");
        } else {
            this.uri = uri;
        }
    }

    @Override
    public void close() throws Exception {
        client.close();
    }
}
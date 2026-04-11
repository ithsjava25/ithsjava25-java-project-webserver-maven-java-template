package org.example;

import org.example.config.AppConfig;
import org.example.filter.Filter;
import org.example.filter.FilterChainImpl;
import org.example.filter.FilterPipelineFactory;
import org.example.httpparser.HttpParser;
import org.example.httpparser.HttpRequest;
import org.example.http.HttpResponseBuilder;
import org.example.config.ConfigLoader;

import java.io.IOException;
import java.net.Socket;
import java.util.List;

public class ConnectionHandler implements AutoCloseable {

    private final Socket client;
    private String uri;
    private final List<Filter> filters;
    private final AppConfig appConfig;
    private String webRoot;


    public ConnectionHandler(Socket client) {
        this.client = client;
        this.appConfig = ConfigLoader.get();
        this.filters = FilterPipelineFactory.build(appConfig);
        this.webRoot = null;
    }

    public ConnectionHandler(Socket client, String webRoot) {
        this.client = client;
        this.webRoot = webRoot;
        this.appConfig = ConfigLoader.get();
        this.filters = FilterPipelineFactory.build(appConfig);

    }

    public void runConnectionHandler() throws IOException {
        StaticFileHandler sfh = (webRoot != null) ? new StaticFileHandler(webRoot) : new StaticFileHandler();

        HttpParser parser = new HttpParser();
        parser.setReader(client.getInputStream());
        parser.parseRequest();
        parser.parseHttp();

        HttpRequest request = new HttpRequest(
                parser.getMethod(),
                parser.getUri(),
                parser.getVersion(),
                parser.getHeadersMap(),
                ""
        );

        String clientIp = client.getInetAddress().getHostAddress();
        request.setAttribute("clientIp", clientIp);

        HttpResponseBuilder response = applyFilters(request);

        int statusCode = response.getStatusCode();
        if (statusCode == HttpResponseBuilder.SC_FORBIDDEN ||
                statusCode == HttpResponseBuilder.SC_BAD_REQUEST) {
            client.getOutputStream().write(response.build());
            client.getOutputStream().flush();
            return;
        }

        resolveTargetFile(parser.getUri());
        sfh.sendGetRequest(client.getOutputStream(), uri);
    }

    private HttpResponseBuilder applyFilters(HttpRequest request) {
        HttpResponseBuilder response = new HttpResponseBuilder();
        FilterChainImpl chain = new FilterChainImpl(filters);
        chain.doFilter(request, response);
        return response;
    }

    private void resolveTargetFile(String uri) {
        if (uri == null || "/".equals(uri)) {
            this.uri = "index.html";
        } else {
            this.uri = uri.startsWith("/") ? uri.substring(1) : uri;
        }
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

}
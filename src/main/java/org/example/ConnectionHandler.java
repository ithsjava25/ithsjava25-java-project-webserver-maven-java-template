package org.example;

import org.example.config.AppConfig;
import org.example.config.ConfigLoader;
import org.example.filter.Filter;
import org.example.filter.FilterChainImpl;
import org.example.filter.IpFilter;
import org.example.http.HttpResponseBuilder;
import org.example.httpparser.HttpParser;
import org.example.httpparser.HttpRequest;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ConnectionHandler implements AutoCloseable {

    private final Socket client;
    private final List<Filter> filters;
    private final String webRoot;

    public ConnectionHandler(Socket client) {
        this.client = client;
        this.webRoot = null;
        this.filters = buildFilters();
    }

    public ConnectionHandler(Socket client, String webRoot) {
        this.client = client;
        this.webRoot = webRoot;
        this.filters = buildFilters();
    }

    private List<Filter> buildFilters() {
        List<Filter> list = new ArrayList<>();
        AppConfig config = ConfigLoader.get();
        AppConfig.IpFilterConfig ipFilterConfig = config.ipFilter();
        if (Boolean.TRUE.equals(ipFilterConfig.enabled())) {
            list.add(createIpFilterFromConfig(ipFilterConfig));
        }
        // Add more filters here if needed
        return list;
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    public void runConnectionHandler() throws IOException {
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

        // Save client IP
        String clientIp = client.getInetAddress().getHostAddress();
        request.setAttribute("clientIp", clientIp);

        HttpResponseBuilder response = new HttpResponseBuilder();

        // Apply filters first
        FilterChainImpl chain = new FilterChainImpl(filters);
        chain.doFilter(request, response);

        // If filters returned forbidden or bad request, send immediately
        int status = response.getStatusCode();
        if (status == HttpResponseBuilder.SC_FORBIDDEN ||
                status == HttpResponseBuilder.SC_BAD_REQUEST) {
            client.getOutputStream().write(response.build());
            client.getOutputStream().flush();
            return;
        }

        String uri = parser.getUri();

        // Handle /health endpoint
        if ("/health".equals(uri) && "GET".equalsIgnoreCase(request.getMethod())) {
            HttpResponseBuilder healthResponse = new HttpResponseBuilder();
            healthResponse.setStatusCode(HttpResponseBuilder.SC_OK);
            healthResponse.setContentType("application/json");
            healthResponse.setBody("{\"status\":\"ok\"}".getBytes());
            client.getOutputStream().write(healthResponse.build());
            client.getOutputStream().flush();
            return;
        }

        // Handle static files
        StaticFileHandler sfh = webRoot != null ? new StaticFileHandler(webRoot) : new StaticFileHandler();
        if ("HEAD".equalsIgnoreCase(request.getMethod())) {
            sfh.sendHeadRequest(client.getOutputStream(), uri);
        } else {
            sfh.sendGetRequest(client.getOutputStream(), uri);
        }
    }

    private IpFilter createIpFilterFromConfig(AppConfig.IpFilterConfig config) {
        IpFilter filter = new IpFilter();

        if ("ALLOWLIST".equalsIgnoreCase(config.mode())) {
            filter.setMode(IpFilter.FilterMode.ALLOWLIST);
        } else {
            filter.setMode(IpFilter.FilterMode.BLOCKLIST);
        }

        for (String ip : config.blockedIps()) filter.addBlockedIp(ip);
        for (String ip : config.allowedIps()) filter.addAllowedIp(ip);

        filter.init();
        return filter;
    }
}
package org.example;

import org.example.config.AppConfig;
import org.example.filter.IpFilter;
import org.example.httpparser.HttpParser;
import org.example.httpparser.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import org.example.filter.Filter;
import org.example.filter.FilterChainImpl;
import org.example.http.HttpResponseBuilder;
import org.example.config.ConfigLoader;

import java.io.IOException;
import java.net.Socket;

public class ConnectionHandler implements AutoCloseable {
    private final Socket client;
    private final List<Filter> filters;
    private final String webRoot;

    public ConnectionHandler(Socket client) {
        this.client = client;
        this.webRoot = "www";
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
        return list;
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

        String clientIp = client.getInetAddress().getHostAddress();
        request.setAttribute("clientIp", clientIp);

        // Apply security filters
        HttpResponseBuilder response = applyFilters(request);

        int statusCode = response.getStatusCode();
        if (statusCode == HttpResponseBuilder.SC_FORBIDDEN ||
                statusCode == HttpResponseBuilder.SC_BAD_REQUEST) {
            byte[] responseBytes = response.build();
            client.getOutputStream().write(responseBytes);
            client.getOutputStream().flush();
            return;
        }

        // Let StaticFileHandler handle everything
        StaticFileHandler sfh = new StaticFileHandler(webRoot);
        sfh.sendGetRequest(client.getOutputStream(), parser.getUri());
    }

    private HttpResponseBuilder applyFilters(HttpRequest request) {
        HttpResponseBuilder response = new HttpResponseBuilder();
        FilterChainImpl chain = new FilterChainImpl(filters);
        chain.doFilter(request, response);
        return response;
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    private IpFilter createIpFilterFromConfig(AppConfig.IpFilterConfig config) {
        IpFilter filter = new IpFilter();

        if ("ALLOWLIST".equalsIgnoreCase(config.mode())) {
            filter.setMode(IpFilter.FilterMode.ALLOWLIST);
        } else {
            filter.setMode(IpFilter.FilterMode.BLOCKLIST);
        }

        for (String ip : config.blockedIps()) {
            filter.addBlockedIp(ip);
        }

        for (String ip : config.allowedIps()) {
            filter.addAllowedIp(ip);
        }

        filter.init();
        return filter;
    }
}
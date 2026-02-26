package org.example;

import org.example.config.AppConfig;
import org.example.filter.IpFilter;
import org.example.httpparser.HttpParser;
import org.example.httpparser.HttpRequest;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
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

    public ConnectionHandler(Socket client) {
        this.client = client;
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

        // Sanitize URI here (clean it)
        String sanitizedUri = sanitizeUri(parser.getUri());
        String cacheKey = "www:" + sanitizedUri;

        // Check cache FIRST
        byte[] cachedBytes = FileCache.get(cacheKey);
        if (cachedBytes != null) {
            System.out.println(" Cache HIT: " + sanitizedUri);
            response.setContentTypeFromFilename(sanitizedUri);
            response.setBody(cachedBytes);
            client.getOutputStream().write(response.build());
            client.getOutputStream().flush();
            return;
        }

        // Cache miss - StaticFileHandler reads and caches
        System.out.println(" Cache MISS: " + sanitizedUri);
        try {
            byte[] fileBytes = Files.readAllBytes(new File("www", sanitizedUri).toPath());
            FileCache.put(cacheKey, fileBytes);  // ← SPARAR I CACHEN HÄR
            
            response.setContentTypeFromFilename(sanitizedUri);
            response.setBody(fileBytes);
            client.getOutputStream().write(response.build());
            client.getOutputStream().flush();
        } catch (NoSuchFileException e) {
            response.setStatusCode(HttpResponseBuilder.SC_NOT_FOUND);
            response.setBody("404 Not Found");
            client.getOutputStream().write(response.build());
            client.getOutputStream().flush();
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

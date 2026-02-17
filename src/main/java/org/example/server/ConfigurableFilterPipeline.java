package org.example.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ConfigurableFilterPipeline {

    private final List<FilterRegistration> registrations;

    public ConfigurableFilterPipeline(List<FilterRegistration> registrations) {
        this.registrations = registrations;
    }

    public HttpResponse execute(HttpRequest request, TerminalHandler handler) {

        List<FilterRegistration> globalRegs = new ArrayList<>();
        List<FilterRegistration> routeRegs = new ArrayList<>();

        for (FilterRegistration reg : registrations) {
            if (reg.isGlobal()) {
                globalRegs.add(reg);
            } else {
                if (matchesAny(reg.routePatterns(), request.path())) {
                    routeRegs.add(reg);
                }
            }
        }

        Comparator<FilterRegistration> byOrder =
                Comparator.comparingInt(FilterRegistration::order);

        globalRegs.sort(byOrder);
        routeRegs.sort(byOrder);

        List<HttpFilter> allFilters = new ArrayList<>(globalRegs.size() + routeRegs.size());
        for (FilterRegistration reg : globalRegs) {
            allFilters.add(reg.filter());
        }
        for (FilterRegistration reg : routeRegs) {
            allFilters.add(reg.filter());
        }

        return buildChain(allFilters, handler).next(request);
    }

    private boolean matchesAny(List<String> patterns, String path) {
        if (patterns == null) return false;

        for (String pattern : patterns) {
            if (RoutePattern.matches(pattern, path)) {
                return true;
            }
        }

        return false;
    }

    private FilterChain buildChain(List<HttpFilter> filters, TerminalHandler handler) {

        FilterChain chain = new FilterChain() {
            @Override
            public HttpResponse next(HttpRequest request) {
                return handler.handle(request);
            }
        };

        for (int i = filters.size() - 1; i >= 0; i--) {

            HttpFilter currentFilter = filters.get(i);
            FilterChain nextChain = chain;

            chain = new FilterChain() {
                @Override
                public HttpResponse next(HttpRequest request) {
                    return currentFilter.handle(request, nextChain);
                }
            };
        }

        return chain;
    }
}

class HttpRequest {
    private final String method;
    private final String path;

    public HttpRequest(String method, String path) {
        this.method = method;
        this.path = path;
    }

    public String path() {
        return path;
    }

    public String getMethod() {
        return method;
    }
}

class HttpResponse {
    private final int statusCode;
    private final String body;

    public HttpResponse(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }
}

interface HttpFilter {
    HttpResponse handle(HttpRequest request, FilterChain chain);
}

interface FilterChain {
    HttpResponse next(HttpRequest request);
}

interface TerminalHandler {
    HttpResponse handle(HttpRequest request);
}
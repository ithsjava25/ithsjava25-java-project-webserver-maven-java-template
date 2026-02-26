package org.example.server;

import org.example.filter.Filter;

import java.util.List;

public record FilterRegistration(
        Filter filter,
        int order,
        List<String> routePatterns
) {

    public boolean isGlobal() {
        return routePatterns == null || routePatterns.isEmpty();
    }
}

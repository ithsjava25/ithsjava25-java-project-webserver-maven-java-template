package org.example.server;

import java.util.List;

public record FilterRegistration(
        HttpFilter filter,
        int order,
        List<String> routePatterns // null/empty = global
) {

    public boolean isGlobal() {
        return routePatterns == null || routePatterns.isEmpty();
    }
}

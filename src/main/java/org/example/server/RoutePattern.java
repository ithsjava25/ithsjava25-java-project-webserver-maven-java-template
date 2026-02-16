package org.example.server;

public final class RoutePattern {

    private RoutePattern() {}

    public static boolean matches(String pattern, String path) {
        if (pattern == null || path == null) return false;

        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return path.startsWith(prefix);
        }

        return pattern.equals(path);
    }
}

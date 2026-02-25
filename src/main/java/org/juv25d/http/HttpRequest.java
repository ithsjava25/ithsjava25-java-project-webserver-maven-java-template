package org.juv25d.http;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public record HttpRequest(
        String method,
        String path,
        @Nullable String queryString,
        String httpVersion,
        Map<String, String> headers,
        byte[] body,
        String remoteIp
) {}

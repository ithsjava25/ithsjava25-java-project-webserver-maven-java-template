package org.example.filter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.http.HttpResponseBuilder;
import org.example.httpparser.HttpRequest;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** A filter that allows or blocks HTTP requests based on the client's IP address.
 ** The filter supports two modes: * ALLOWLIST – only IP addresses in the allowlist are permitted.
 ** BLOCKLIST – all IP addresses are permitted except those in the blocklist */

public class IpFilter implements Filter {

    private final Set<String> blockedIps = ConcurrentHashMap.newKeySet();
    private final Set<String> allowedIps = ConcurrentHashMap.newKeySet();
    private volatile FilterMode mode = FilterMode.BLOCKLIST;


    public enum FilterMode {
        ALLOWLIST,
        BLOCKLIST
    }

    public IpFilter(java.util.Map<String, Object> configMap) {

        ObjectMapper mapper = new ObjectMapper();

        IpFilterConfig config = mapper
                .convertValue(configMap, IpFilterConfig.class)
                .withDefaultsApplied();

        if (config.enabled() != null && !config.enabled()) {
            return;
        }

        this.mode = FilterMode.valueOf(config.mode().toUpperCase());

        config.blockedIps().forEach(this::addBlockedIp);
        config.allowedIps().forEach(this::addAllowedIp);
    }

    @Override
    public void init() {

    }

    @Override
    public void doFilter(HttpRequest request, HttpResponseBuilder response, FilterChain chain) {

        String clientIp = normalizeIp((String) request.getAttribute("clientIp"));

        if (clientIp == null || clientIp.trim().isEmpty()) {
            response.setStatusCode(HttpResponseBuilder.SC_BAD_REQUEST);
            response.setBody("Bad Request: Missing client IP address");
            return;
        }

        boolean allowed = isIpAllowed(clientIp);

        if (allowed) {
            chain.doFilter(request, response);
        } else {
            response.setStatusCode(HttpResponseBuilder.SC_FORBIDDEN);
            response.setBody("Forbidden: IP address " + clientIp + " is not allowed");
        }
    }

    private boolean isIpAllowed(String ip) {
        if (mode == FilterMode.ALLOWLIST) {
            return allowedIps.contains(ip);
        } else {
            return !blockedIps.contains(ip);
        }
    }

    private String normalizeIp(String ip) {
        return ip == null ? null : ip.trim();
    }

    public void setMode(FilterMode mode) {
        this.mode = mode;
    }

    public void addBlockedIp(String ip) {
        if (ip == null) {
            throw new IllegalArgumentException("IP address cannot be null");
        }
        blockedIps.add(normalizeIp(ip));
    }

    public void addAllowedIp(String ip) {
        if (ip == null) {
            throw new IllegalArgumentException("IP address cannot be null");
        }
        allowedIps.add(normalizeIp(ip));
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IpFilterConfig(
            Boolean enabled,
            String mode,
            java.util.List<String> blockedIps,
            java.util.List<String> allowedIps
    ) {
        public IpFilterConfig withDefaultsApplied() {
            Boolean e = enabled != null && enabled;
            String m = (mode == null || mode.isBlank()) ? "BLOCKLIST" : mode;
            java.util.List<String> blocked = (blockedIps == null) ? java.util.List.of() : blockedIps;
            java.util.List<String> allowed = (allowedIps == null) ? java.util.List.of() : allowedIps;

            return new IpFilterConfig(e, m, blocked, allowed);
        }
    }
}
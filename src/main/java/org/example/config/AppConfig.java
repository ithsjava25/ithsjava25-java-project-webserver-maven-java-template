package org.example.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AppConfig(
        @JsonProperty("server") ServerConfig server,
        @JsonProperty("logging") LoggingConfig logging,
        @JsonProperty("filters") List<FilterConfig> filters
) {

    public static AppConfig defaults() {
        return new AppConfig(
                ServerConfig.defaults(),
                LoggingConfig.defaults(),
                List.of()
        );
    }

    public AppConfig withDefaultsApplied() {
        ServerConfig serverConfig =
                (server == null ? ServerConfig.defaults() : server.withDefaultsApplied());

        LoggingConfig loggingConfig =
                (logging == null ? LoggingConfig.defaults() : logging.withDefaultsApplied());

        List<FilterConfig> filterList =
                (filters == null ? List.of() : filters);

        return new AppConfig(serverConfig, loggingConfig, filterList);
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FilterConfig(
            @JsonProperty("type") String type,
            @JsonProperty("config") Map<String, Object> config
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ServerConfig(
            @JsonProperty("port") Integer port,
            @JsonProperty("rootDir") String rootDir
    ) {
        public static ServerConfig defaults() {
            return new ServerConfig(8080, "./www");
        }

        public ServerConfig withDefaultsApplied() {
            int p = (port == null ? 8080 : port);
            if (p < 1 || p > 65535) {
                throw new IllegalArgumentException("Invalid port number: " + p);
            }
            String rd = (rootDir == null || rootDir.isBlank()) ? "./www" : rootDir;

            return new ServerConfig(p, rd);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoggingConfig(
            @JsonProperty("level") String level
    ) {
        public static LoggingConfig defaults() {
            return new LoggingConfig("INFO");
        }

        public LoggingConfig withDefaultsApplied() {
            String lvl = (level == null || level.isBlank()) ? "INFO" : level;
            return new LoggingConfig(lvl);
        }
    }
}
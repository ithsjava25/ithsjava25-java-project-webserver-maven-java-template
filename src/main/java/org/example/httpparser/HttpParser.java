package org.example.httpparser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HttpParser extends HttpParseRequestLine {
    private final Map<String, String> headersMap = new HashMap<>();
    private BufferedReader reader;

    public void setReader(InputStream in) {
        if (this.reader == null) {
            this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    public void parseHttp() throws IOException {
        String headerLine;

        while ((headerLine = reader.readLine()) != null) {
            if (headerLine.isEmpty()) {
                break;
            }

            int valueSeparator = headerLine.indexOf(':');
            if (valueSeparator <= 0) {
                continue;
            }

            String key = headerLine.substring(0, valueSeparator).trim();
            String value = headerLine.substring(valueSeparator + 1).trim();

            headersMap.merge(key, value, (existing, incoming) -> existing +", " + incoming);
        }
    }


    public void parseRequest() throws IOException {
        parseHttpRequest(reader);
    }

    public Map<String, String> getHeadersMap() {
        return headersMap;
    }

}

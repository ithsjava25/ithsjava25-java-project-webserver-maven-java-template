
package org.juv25d.filter;

import org.juv25d.http.HttpRequest;
import org.juv25d.http.HttpResponse;
import java.io.IOException;

    /**
     * Filter som lägger till säkerhetsheaders till varje HTTP-svar.
     * Detta hjälper till att skydda mot attacker som Clickjacking och MIME-sniffing.
     */
    public class SecurityHeadersFilter implements Filter {

        @Override
        public void doFilter(HttpRequest req, HttpResponse res, FilterChain chain) throws IOException {
            chain.doFilter(req, res);

            res.setHeader("X-Content-Type-Options", "nosniff");
            res.setHeader("X-Frame-Options", "DENY");
            res.setHeader("X-XSS-Protection", "1; mode=block");
            res.setHeader("Referrer-Policy", "no-referrer");
        }
    }



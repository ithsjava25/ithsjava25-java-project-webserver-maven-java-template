package org.example.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.example.http.HttpResponseBuilder;
import org.example.httpparser.HttpRequest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Rate Limiting Filter responsible for limiting the number of requests per client IP.
 * Implements the Token Bucket algorithm using the Bucket4j library.
 * How it works:
 * A "bucket" hold a fixed number of tokens (capacity)
 * Each incoming request attempts to consume exactly one token
 * If a token is available, the request is processed and the token is removed
 * If the bucket is empty, the request is rejected with an HTTP 429 (Too Many Requests) status
 * Tokens are replenished at a fixed rate over time (Refill Rate), up to the maximum capaci
 * This allows for occasional bursts of traffic while maintaining a steady long-term rate limit
 * The capacity of the bucket is 10, and it refills one token per 10 seconds
 */

public class RateLimitingFilter implements Filter {
    private static final Logger logger = Logger.getLogger(RateLimitingFilter.class.getName());
    private static final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private static final long capacity = 10;
    private static final long refillTokens = 1;
    private final Duration refillPeriod = Duration.ofSeconds(10);

    @Override
    public void init() {
        logger.info("RateLimitingFilter initialized with capacity: " + capacity);
    }

    /**
     * Intercepts the request and checks if the client has enough tokens.
     */

    @Override
    public void doFilter(HttpRequest request, HttpResponseBuilder response, FilterChain chain) {

        String clientIp = (String) request.getAttribute("clientIp");

        Bucket bucket = buckets.computeIfAbsent(clientIp, k -> createNewBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            logger.warning("Limit exceeded per IP: " + clientIp);
            response.setStatusCode(HttpResponseBuilder.SC_TOO_MANY_REQUESTS);
            response.setBody("<h1>429 Too Many Requests</h1><p> Limit of requests exceeded.</p>\n");
        }
    }

    @Override
    public void destroy() {
        buckets.clear();
    }

    /**
     * Configures a new Bucket with the specified bandwidth.
     */
    private Bucket createNewBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(refillTokens, refillPeriod)
                        .build())
                .build();
    }

    public void clearBuckets(){
             buckets.clear();
    }
}

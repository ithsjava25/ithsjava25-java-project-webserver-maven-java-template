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
 * Filter responsible for limiting the number of requests per client IP.
 * Implements the Token Bucket algorithm using the Bucket4j library.
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
            response.setBody("429 Too Many Requests: limit of requests exceeded.\n");
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

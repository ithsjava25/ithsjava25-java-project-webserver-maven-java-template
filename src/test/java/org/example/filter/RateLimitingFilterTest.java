package org.example.filter;

import org.example.http.HttpResponseBuilder;
import org.example.httpparser.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpResponse;
import java.util.HashMap;

import static org.example.http.HttpResponseBuilder.SC_OK;
import static org.example.http.HttpResponseBuilder.SC_TOO_MANY_REQUESTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitingFilterTest {

    @Mock
    FilterChain filterChain;

    private RateLimitingFilter filter;
    private HttpRequest request;
    private HttpResponseBuilder response;

    @BeforeEach
    void setUp(){
        filter = new RateLimitingFilter();
        request = new HttpRequest("GET", "/", "HTTP/1.1", new HashMap<>(), "");
        request.setAttribute("clientIp", "127.0.0.1");
        response = new HttpResponseBuilder();
        filter.clearBuckets();
    }

    @Test
    void shouldAllowRequestWhenTokensAreAvailable(){

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertEquals(SC_OK, response.getStatusCode());
    }

    @Test
    void shouldNotAllowRequestWhenTokensAreNotAvailable(){

        //capacity of the bucket is 10
        for(int i = 0; i < 11; i++ )
            filter.doFilter(request, response, filterChain);

        assertEquals(SC_TOO_MANY_REQUESTS, response.getStatusCode());
        verify(filterChain, times(10)).doFilter(any(), any());
    }

    @Test
    void shouldHaveSeparateBucketsPerIp(){

        //Request 1
        for(int i = 0; i < 11; i++)
            filter.doFilter(request, response, filterChain);

        //Request 2 with a different Ip
        HttpRequest request2 = new HttpRequest("GET", "/", "HTTP/1.1", new HashMap<>(), "");
        request2.setAttribute("clientIp", "127.2.2.2");
        HttpResponseBuilder response2 = new HttpResponseBuilder();

        filter.doFilter(request2, response2, filterChain);

        //First request should be 429 because it exceeded the capacity of the bucket (10)
        assertEquals(SC_TOO_MANY_REQUESTS, response.getStatusCode());
        //Second request should be 200
        assertEquals(SC_OK, response2.getStatusCode());
    }


}

package org.juv25d.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HttpResponseTest {

    private HttpResponse response;

    @BeforeEach
    void setUp() {
        response = new HttpResponse();
    }

    @Test
    void shouldReturnDefaultStatusCode() {
        assertEquals(0, response.statusCode());
    }

    @Test
    void shouldReturnDefaultText() {
        assertEquals("", response.statusText());
    }

    @Test
    void shouldThrowExceptionWhenStatusTextIsNull() {
        assertThrows(NullPointerException.class, () -> response.setStatusText(null));
    }

    @Test
    void shouldHaveEmptyHeaderByDefault() {
        assertTrue(response.headers().isEmpty());
    }

    @Test
    void shouldHaveEmptyBodyByDefault() {
        assertArrayEquals(new byte[0], response.body());
    }
}

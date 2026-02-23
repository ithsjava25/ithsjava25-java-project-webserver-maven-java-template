package org.juv25d.http;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HttpResponseTest {

    @Test
    void shouldReturnDefaultValue() {
        HttpResponse response = new HttpResponse();
        assertEquals(0, response.statusCode());
    }

    @Test
    void shouldReturnDefaultText() {
        HttpResponse response = new HttpResponse();
        assertNull(response.statusText());
    }

    @Test
    void shouldThrowExceptionWhenStatusTextIsNull() {
        HttpResponse response = new HttpResponse();
        assertThrows(NullPointerException.class, () -> response.setStatusText(null));
    }

    @Test
    void shouldHaveEmptyHeaderByDefault() {
        HttpResponse response = new HttpResponse();
        assertTrue(response.headers().isEmpty());
    }

    @Test
    void shouldHaveEmptyBodyByDefault() {
        HttpResponse response = new HttpResponse();
        assertEquals(0, response.body().length);
    }
}

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
    void shouldSetAndReturnStatusCode() {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(404);
        assertEquals(404, response.statusCode());
    }

    @Test
    void shouldReturnDefaultText() {
        HttpResponse response = new HttpResponse();
        assertNull(response.statusText());
    }

    @Test
    void shouldSetAndReturnStatusText() {
        HttpResponse response = new HttpResponse();
        response.setStatusText("Not found");
        assertEquals("Not found", response.statusText());
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
    void shouldSetAndReturnHeader() {
        HttpResponse response = new HttpResponse();
        response.setHeader("Content-Type", "text/plain");
        assertEquals("text/plain", response.headers().get("Content-Type"));
    }

    @Test
    void shouldHaveEmptyBodyByDefault() {
        HttpResponse response = new HttpResponse();
        assertEquals(0, response.body().length);
    }

    @Test
    void shouldSetAndReturnBody() {
        HttpResponse response = new HttpResponse();
        byte[] body = "Hello World".getBytes();
        response.setBody(body);
        assertArrayEquals(body, response.body());
    }
}

package org.juv25d.http;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

}

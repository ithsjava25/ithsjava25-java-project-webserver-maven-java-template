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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class HttpResponseTest {

    @Test
    void defaultConstructor_hasSafeDefaults_andSetHeaderDoesNotThrow() {
        HttpResponse response = new HttpResponse();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.statusText()).isEqualTo("OK");
        assertThat(response.headers()).isNotNull();
        assertThat(response.body()).isNotNull();
        assertThat(response.body()).isEmpty();

        assertThatCode(() -> response.setHeader("Content-Type", "text/plain"))
            .doesNotThrowAnyException();

        assertThat(response.headers()).containsEntry("Content-Type", "text/plain");
    }
}

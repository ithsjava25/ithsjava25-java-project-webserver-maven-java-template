package org.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test class for verifying the behavior of the StaticFileHandler class.
 *
 * This test class ensures that StaticFileHandler correctly handles GET requests
 * for static files, including both cases where the requested file exists and
 * where it does not. Temporary directories and files are utilized in tests to
 * ensure no actual file system dependencies during test execution.
 *
 * Key functional aspects being tested include:
 * - Correct response status code and content for an existing file.
 * - Correct response status code and fallback behavior for a missing file.
 */
class StaticFileHandlerTest {

    private StaticFileHandler createHandler() {
        return new StaticFileHandler(tempDir.toString());
    }

    private String sendRequest(String uri) throws IOException {
        StaticFileHandler handler = createHandler();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        handler.sendGetRequest(output, uri);
        return output.toString();
    }

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Rensa cache innan varje test för clean state
        StaticFileHandler.clearCache();
    }

    @Test
    void testCaching_HitOnSecondRequest() throws IOException {
        // Arrange
        Files.writeString(tempDir.resolve("cached.html"), "Content");

        // Act
        sendRequest("cached.html");
        int sizeAfterFirst = StaticFileHandler.getCacheStats().entries;
        sendRequest("cached.html");
        int sizeAfterSecond = StaticFileHandler.getCacheStats().entries;

        // Assert
        assertThat(sizeAfterFirst).isEqualTo(sizeAfterSecond).isEqualTo(1);
    }

    @Test
    void testSanitization_QueryString() throws IOException {
        Files.writeString(tempDir.resolve("index.html"), "Home");
        assertThat(sendRequest("index.html?foo=bar")).contains("HTTP/1.1 200");
    }

    @Test
    void testSanitization_LeadingSlash() throws IOException {
        Files.writeString(tempDir.resolve("page.html"), "Page");
        assertThat(sendRequest("/page.html")).contains("HTTP/1.1 200");
    }

    @Test
    void testSanitization_NullBytes() throws IOException {
        assertThat(sendRequest("file.html\0../../secret")).contains("HTTP/1.1 404");
    }

    @Test
    void testConcurrent_MultipleReads() throws InterruptedException, IOException {
        // Arrange
        Files.writeString(tempDir.resolve("shared.html"), "Data");
        StaticFileHandler handler = new StaticFileHandler(tempDir.toString());

        // Förvärmning - ladda filen i cache
        handler.sendGetRequest(new ByteArrayOutputStream(), "shared.html");

        // Act - 10 trådar läser samma fil 50 gånger varje = 500 totala läsningar
        Thread[] threads = new Thread[10];
        final Exception[] threadError = new Exception[1];

        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < 50; j++) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        handler.sendGetRequest(out, "shared.html");
                        String response = out.toString();
                        
                        // Validera att svaret är korrekt
                        if (!response.contains("HTTP/1.1 200") || !response.contains("Data")) {
                            throw new AssertionError("Oväntad response: " + response.substring(0, Math.min(100, response.length())));
                        }
                    }
                } catch (Exception e) {
                    synchronized (threads) {
                        threadError[0] = e;
                    }
                }
            });
            threads[i].start();
        }

        // Vänta på alla trådar
        for (Thread t : threads) {
            t.join();
        }

        // Assert - Kontrollera om någon tråd hade fel
        if (threadError[0] != null) {
            throw new AssertionError("Tråd-fel: " + threadError[0].getMessage(), threadError[0]);
        }

        // Assert - Cache ska bara ha EN entry för shared.html
        FileCache.CacheStats stats = StaticFileHandler.getCacheStats();
        assertThat(stats.entries).isEqualTo(1);
        assertThat(stats.totalAccesses).isGreaterThanOrEqualTo(500);
    }

    @Test
    void test_file_that_exists_should_return_200() throws IOException {
        // Arrange
        Path testFile = tempDir.resolve("test.html");
        Files.writeString(testFile, "Hello Test");

        StaticFileHandler staticFileHandler = new StaticFileHandler(tempDir.toString());
        ByteArrayOutputStream fakeOutput = new ByteArrayOutputStream();

        // Act
        staticFileHandler.sendGetRequest(fakeOutput, "test.html");

        // Assert
        String response = fakeOutput.toString();
        assertTrue(response.contains("HTTP/1.1 200 OK"));
        assertTrue(response.contains("Hello Test"));
        assertTrue(response.contains("Content-Type: text/html; charset=UTF-8"));
    }

    @Test
    void test_file_that_does_not_exists_should_return_404() throws IOException {
        // Arrange
        StaticFileHandler staticFileHandler = new StaticFileHandler(tempDir.toString());
        ByteArrayOutputStream fakeOutput = new ByteArrayOutputStream();

        // Act
        staticFileHandler.sendGetRequest(fakeOutput, "notExistingFile.html");

        // Assert
        String response = fakeOutput.toString();
        assertTrue(response.contains("HTTP/1.1 404 Not Found"));
    }

    @Test
    void test_path_traversal_should_return_403() throws IOException {
        // Arrange
        Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret, "TOP SECRET");
        Path webRoot = tempDir.resolve("www");
        Files.createDirectories(webRoot);
        StaticFileHandler handler = new StaticFileHandler(webRoot.toString());
        ByteArrayOutputStream fakeOutput = new ByteArrayOutputStream();

        // Act
        handler.sendGetRequest(fakeOutput, "../secret.txt");

        // Assert
        String response = fakeOutput.toString();
        assertFalse(response.contains("TOP SECRET"));
        assertTrue(response.contains("HTTP/1.1 403 Forbidden"));
    }

    @ParameterizedTest
    @CsvSource({
            "index.html?foo=bar",
            "index.html#section",
            "/index.html"
    })
    void sanitized_uris_should_return_200(String uri) throws IOException {
        // Arrange
        Path webRoot = tempDir.resolve("www");
        Files.createDirectories(webRoot);
        Files.writeString(webRoot.resolve("index.html"), "Hello");
        StaticFileHandler handler = new StaticFileHandler(webRoot.toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Act
        handler.sendGetRequest(out, uri);

        // Assert
        assertTrue(out.toString().contains("HTTP/1.1 200 OK"));
    }

    @Test
    void null_byte_injection_should_not_return_200() throws IOException {
        // Arrange
        Path webRoot = tempDir.resolve("www");
        Files.createDirectories(webRoot);
        StaticFileHandler handler = new StaticFileHandler(webRoot.toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Act
        handler.sendGetRequest(out, "index.html\0../../etc/passwd");

        // Assert
        String response = out.toString();
        assertFalse(response.contains("HTTP/1.1 200 OK"));
        assertTrue(response.contains("HTTP/1.1 404 Not Found"));
    }
}

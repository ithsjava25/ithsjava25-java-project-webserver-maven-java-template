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

    private StaticFileHandler handler;

    //Junit creates a temporary folder which can be filled with temporary files that gets removed after tests
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
        StaticFileHandler handler = new StaticFileHandler(tempDir.toString());

        // Act - Första anropet (cache miss)
        handler.sendGetRequest(new ByteArrayOutputStream(), "cached.html");
        int sizeAfterFirst = StaticFileHandler.getCacheStats().entries;

        // Act - Andra anropet (cache hit)
        handler.sendGetRequest(new ByteArrayOutputStream(), "cached.html");
        int sizeAfterSecond = StaticFileHandler.getCacheStats().entries;

        // Assert - Cache ska innehålla samma entry
        assertThat(sizeAfterFirst).isEqualTo(1);
        assertThat(sizeAfterSecond).isEqualTo(1);
    }

    @Test
    void testSanitization_QueryString() throws IOException {
        // Arrange
        Files.writeString(tempDir.resolve("index.html"), "Home");
        StaticFileHandler handler = new StaticFileHandler(tempDir.toString());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Act - URI med query string
        handler.sendGetRequest(output, "index.html?foo=bar");

        // Assert
        assertThat(output.toString()).contains("HTTP/1.1 200");
    }

    @Test
    void testSanitization_LeadingSlash() throws IOException {
        // Arrange
        Files.writeString(tempDir.resolve("page.html"), "Page");
        StaticFileHandler handler = new StaticFileHandler(tempDir.toString());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Act
        handler.sendGetRequest(output, "/page.html");

        // Assert
        assertThat(output.toString()).contains("HTTP/1.1 200");
    }

    @Test
    void testSanitization_NullBytes() throws IOException {
        // Arrange
        StaticFileHandler handler = new StaticFileHandler(tempDir.toString());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Act
        handler.sendGetRequest(output, "file.html\0../../secret");

        // Assert
        assertThat(output.toString()).contains("HTTP/1.1 404");
    }

    @Test
    void testConcurrent_MultipleReads() throws InterruptedException, IOException {
        // Arrange
        Files.writeString(tempDir.resolve("shared.html"), "Data");
        StaticFileHandler handler = new StaticFileHandler(tempDir.toString());

        // Förvärmning
        handler.sendGetRequest(new ByteArrayOutputStream(), "shared.html");

        // Act - 10 trådar läser samma fil 50 gånger varje
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < 50; j++) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        handler.sendGetRequest(out, "shared.html");
                        assertThat(out.toString()).contains("200");
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            threads[i].start();
        }

        // Vänta på alla trådar
        for (Thread t : threads) {
            t.join();
        }

        // Assert - Cache ska bara ha EN entry
        assertThat(StaticFileHandler.getCacheStats().entries).isEqualTo(1);
    }

@Test
    void test_file_that_exists_should_return_200() throws IOException {
        //Arrange
        Path testFile = tempDir.resolve("test.html"); // Defines the path in the temp directory
        Files.writeString(testFile, "Hello Test"); // Creates a text in that file

        //Using the new constructor in StaticFileHandler to reroute so the tests uses the temporary folder instead of the hardcoded www
        StaticFileHandler staticFileHandler = new StaticFileHandler(tempDir.toString());

        //Using ByteArrayOutputStream instead of Outputstream during tests to capture the servers response in memory, fake stream
        ByteArrayOutputStream fakeOutput = new ByteArrayOutputStream();

        //Act
        staticFileHandler.sendGetRequest(fakeOutput, "test.html"); //Get test.html and write the answer to fakeOutput

        //Assert
        String response = fakeOutput.toString();//Converts the captured byte stream into a String for verification

        assertTrue(response.contains("HTTP/1.1 200 OK")); // Assert the status
        assertTrue(response.contains("Hello Test")); //Assert the content in the file

        assertTrue(response.contains("Content-Type: text/html; charset=UTF-8")); // Verify the correct Content-type header

    }

    @Test
    void test_file_that_does_not_exists_should_return_404() throws IOException {
        //Arrange
        // Pre-create the mandatory error page in the temp directory to prevent NoSuchFileException
        Path testFile = tempDir.resolve("pageNotFound.html");
        Files.writeString(testFile, "Fallback page");

        //Using the new constructor in StaticFileHandler to reroute so the tests uses the temporary folder instead of the hardcoded www
        StaticFileHandler staticFileHandler = new StaticFileHandler(tempDir.toString());

        //Using ByteArrayOutputStream instead of Outputstream during tests to capture the servers response in memory, fake stream
        ByteArrayOutputStream fakeOutput = new ByteArrayOutputStream();

        //Act
        staticFileHandler.sendGetRequest(fakeOutput, "notExistingFile.html"); // Request a file that clearly doesn't exist to trigger the 404 logic

        //Assert
        String response = fakeOutput.toString();//Converts the captured byte stream into a String for verification

        assertTrue(response.contains("HTTP/1.1 404 Not Found")); // Assert the status

    }

    @Test
    void test_path_traversal_should_return_403() throws IOException {
        // Arrange
        Path secret = tempDir.resolve("secret.txt");
        Files.writeString(secret,"TOP SECRET");
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

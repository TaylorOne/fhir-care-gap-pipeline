package io.github.taylorone.fhirpipeline.ingestion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Shared test helper for loading classpath fixtures. */
public final class TestResources {

    private TestResources() {
    }

    public static String read(String path) {
        try (var stream = TestResources.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalArgumentException("Test resource not found: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

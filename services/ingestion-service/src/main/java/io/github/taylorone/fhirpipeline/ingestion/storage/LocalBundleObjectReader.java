package io.github.taylorone.fhirpipeline.ingestion.storage;

import io.github.taylorone.fhirpipeline.ingestion.config.IngestionProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local-profile stand-in for GCS: resolves the event's object name against a
 * directory on disk. Lets the full ingestion path (event → parse → transform →
 * FHIR store) run on a laptop by POSTing a synthetic CloudEvent with curl.
 */
@Component
@Profile("local")
public class LocalBundleObjectReader implements BundleObjectReader {

    private static final Logger log = LoggerFactory.getLogger(LocalBundleObjectReader.class);

    private final Path baseDir;

    public LocalBundleObjectReader(IngestionProperties properties) {
        this.baseDir = Path.of(properties.localBundleDir()).toAbsolutePath().normalize();
        log.info("Local bundle reader serving files under {}", baseDir);
    }

    @Override
    public String read(String bucket, String objectName) {
        Path resolved = baseDir.resolve(objectName).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new BundleReadException(
                    "Object name escapes the local bundle directory: " + objectName, null);
        }
        try {
            return Files.readString(resolved, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BundleReadException("Failed to read local file " + resolved, e);
        }
    }
}

package io.github.taylorone.fhirpipeline.ingestion.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Reads bundle objects from Cloud Storage. Synthea patient bundles are a few
 * MB at most, so reading into memory is appropriate; if this pipeline ever
 * ingests bulk-export NDJSON, this is the seam where a streaming reader slots
 * in.
 */
@Component
@Profile("!local")
public class GcsBundleObjectReader implements BundleObjectReader {

    private final Storage storage;

    public GcsBundleObjectReader(Storage storage) {
        this.storage = storage;
    }

    @Override
    public String read(String bucket, String objectName) {
        try {
            byte[] content = storage.readAllBytes(BlobId.of(bucket, objectName));
            return new String(content, StandardCharsets.UTF_8);
        } catch (StorageException e) {
            throw new BundleReadException(
                    "Failed to read gs://%s/%s: %s".formatted(bucket, objectName, e.getMessage()), e);
        }
    }
}

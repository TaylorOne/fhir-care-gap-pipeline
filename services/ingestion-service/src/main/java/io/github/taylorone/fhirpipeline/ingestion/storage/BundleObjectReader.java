package io.github.taylorone.fhirpipeline.ingestion.storage;

/**
 * Seam for reading uploaded bundle objects. Production reads from Cloud
 * Storage; the local profile reads from a directory on disk so the whole
 * service runs without GCP credentials.
 */
public interface BundleObjectReader {

    /**
     * @return the object's content as UTF-8 text
     * @throws BundleReadException if the object cannot be read (missing,
     *         permission-denied, …) — treated as permanent for this delivery
     */
    String read(String bucket, String objectName);
}

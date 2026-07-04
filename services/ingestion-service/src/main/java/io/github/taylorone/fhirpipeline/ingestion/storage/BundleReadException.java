package io.github.taylorone.fhirpipeline.ingestion.storage;

/**
 * The referenced object could not be read. Classified as permanent because the
 * GCS client library already retries retryable storage errors internally with
 * its own backoff; what surfaces here (object deleted, IAM denied) will not be
 * fixed by Eventarc redelivery.
 */
public class BundleReadException extends RuntimeException {

    public BundleReadException(String message, Throwable cause) {
        super(message, cause);
    }
}

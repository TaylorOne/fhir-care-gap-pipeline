package io.github.taylorone.fhirpipeline.ingestion.fhir;

/**
 * The uploaded object is not a well-formed FHIR R4 transaction bundle.
 * Permanent by definition — redelivering the same bytes cannot succeed.
 */
public class BundleParseException extends RuntimeException {

    public BundleParseException(String message) {
        super(message);
    }

    public BundleParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

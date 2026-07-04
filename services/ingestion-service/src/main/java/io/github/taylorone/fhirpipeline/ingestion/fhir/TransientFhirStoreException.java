package io.github.taylorone.fhirpipeline.ingestion.fhir;

/**
 * A FHIR store failure that is expected to succeed on retry: rate limiting
 * (429), server errors (5xx), connection failures, or credential refresh
 * hiccups. The retry policy retries these; if the budget is exhausted the
 * event handler answers non-2xx so Eventarc redelivers the event later.
 */
public class TransientFhirStoreException extends RuntimeException {

    public TransientFhirStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}

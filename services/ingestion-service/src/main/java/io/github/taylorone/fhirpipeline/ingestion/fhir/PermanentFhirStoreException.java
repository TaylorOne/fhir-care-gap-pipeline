package io.github.taylorone.fhirpipeline.ingestion.fhir;

/**
 * A FHIR store rejection that no amount of retrying will fix — 4xx responses
 * such as validation failures or malformed references. These are logged and
 * acknowledged (2xx to Eventarc) so the poison bundle is not redelivered
 * forever; the raw file stays in the ingest bucket for offline diagnosis.
 */
public class PermanentFhirStoreException extends RuntimeException {

    public PermanentFhirStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}

package io.github.taylorone.fhirpipeline.ingestion.fhir;

import org.hl7.fhir.r4.model.Bundle;

/**
 * Seam for the FHIR store. The production implementation speaks FHIR R4 REST
 * via HAPI; tests substitute a fake without any HTTP.
 */
public interface FhirStoreClient {

    /**
     * Executes a transaction bundle against the store.
     *
     * @return the transaction-response bundle from the server
     * @throws TransientFhirStoreException for failures worth retrying
     * @throws PermanentFhirStoreException for rejections that will never succeed
     */
    Bundle executeTransaction(Bundle bundle);
}

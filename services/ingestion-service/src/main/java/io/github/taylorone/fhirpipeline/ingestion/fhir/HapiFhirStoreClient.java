package io.github.taylorone.fhirpipeline.ingestion.fhir;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Component;

/**
 * FHIR store access via HAPI's generic client. The single responsibility of
 * this class beyond delegation is <em>failure classification</em>: mapping the
 * zoo of HAPI exceptions onto the pipeline's two categories — transient
 * (retry) and permanent (acknowledge and log). Keeping classification here
 * means the retry policy and the event handler never inspect HTTP codes.
 */
@Component
public class HapiFhirStoreClient implements FhirStoreClient {

    /**
     * 429: rate limit. 500/502/503/504: server-side or intermediary failures.
     * Everything else in 4xx is a deterministic rejection of this bundle.
     */
    private static final Set<Integer> TRANSIENT_STATUS = Set.of(429, 500, 502, 503, 504);

    private final IGenericClient client;

    public HapiFhirStoreClient(IGenericClient client) {
        this.client = client;
    }

    @Override
    public Bundle executeTransaction(Bundle bundle) {
        try {
            return client.transaction().withBundle(bundle).execute();
        } catch (FhirClientConnectionException e) {
            throw new TransientFhirStoreException("FHIR store connection failure", e);
        } catch (BaseServerResponseException e) {
            int status = e.getStatusCode();
            if (TRANSIENT_STATUS.contains(status)) {
                throw new TransientFhirStoreException("FHIR store returned " + status, e);
            }
            throw new PermanentFhirStoreException(
                    "FHIR store rejected bundle with " + status + ": " + e.getMessage(), e);
        }
    }
}

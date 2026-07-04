package io.github.taylorone.fhirpipeline.ingestion.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import io.github.taylorone.fhirpipeline.ingestion.TestResources;
import io.github.taylorone.fhirpipeline.ingestion.fhir.BundleParseException;
import io.github.taylorone.fhirpipeline.ingestion.fhir.BundleParser;
import io.github.taylorone.fhirpipeline.ingestion.fhir.BundleTransformer;
import io.github.taylorone.fhirpipeline.ingestion.fhir.FhirStoreClient;
import io.github.taylorone.fhirpipeline.ingestion.fhir.PermanentFhirStoreException;
import io.github.taylorone.fhirpipeline.ingestion.fhir.TransientFhirStoreException;
import io.github.taylorone.fhirpipeline.ingestion.storage.BundleObjectReader;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.retry.support.RetryTemplate;

/**
 * Uses the real parser, transformer, and a real (fast) RetryTemplate; only the
 * two I/O seams — object reader and FHIR store — are test doubles. This keeps
 * the test honest about the collaboration between retry policy and failure
 * classification instead of mocking the interesting behavior away.
 */
class IngestionServiceTest {

    private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();
    private static final String BUNDLE_JSON =
            TestResources.read("/bundles/synthea-patient-bundle.json");

    private final BundleObjectReader reader = mock(BundleObjectReader.class);
    private final FhirStoreClient fhirStoreClient = mock(FhirStoreClient.class);

    private IngestionService service;

    @BeforeEach
    void setUp() {
        RetryTemplate retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(1)
                .retryOn(TransientFhirStoreException.class)
                .build();
        service = new IngestionService(
                reader,
                new BundleParser(FHIR_CONTEXT),
                new BundleTransformer(),
                fhirStoreClient,
                retryTemplate);
    }

    @Test
    void ingestsBundleAndReportsResourceCount() {
        when(reader.read("bucket", "patient.json")).thenReturn(BUNDLE_JSON);
        when(fhirStoreClient.executeTransaction(any())).thenReturn(new Bundle());

        IngestionResult result = service.ingest("bucket", "patient.json");

        assertThat(result.resourceCount()).isEqualTo(3);
        verify(fhirStoreClient).executeTransaction(any());
    }

    @Test
    void retriesTransientStoreFailuresUntilSuccess() {
        when(reader.read(any(), any())).thenReturn(BUNDLE_JSON);
        when(fhirStoreClient.executeTransaction(any()))
                .thenThrow(new TransientFhirStoreException("503", null))
                .thenThrow(new TransientFhirStoreException("503", null))
                .thenReturn(new Bundle());

        IngestionResult result = service.ingest("bucket", "patient.json");

        assertThat(result.resourceCount()).isEqualTo(3);
        verify(fhirStoreClient, times(3)).executeTransaction(any());
    }

    @Test
    void propagatesTransientFailureWhenRetriesExhausted() {
        when(reader.read(any(), any())).thenReturn(BUNDLE_JSON);
        when(fhirStoreClient.executeTransaction(any()))
                .thenThrow(new TransientFhirStoreException("503", null));

        assertThatThrownBy(() -> service.ingest("bucket", "patient.json"))
                .isInstanceOf(TransientFhirStoreException.class);
        verify(fhirStoreClient, times(3)).executeTransaction(any());
    }

    @Test
    void doesNotRetryPermanentStoreRejections() {
        when(reader.read(any(), any())).thenReturn(BUNDLE_JSON);
        when(fhirStoreClient.executeTransaction(any()))
                .thenThrow(new PermanentFhirStoreException("422 rejected", null));

        assertThatThrownBy(() -> service.ingest("bucket", "patient.json"))
                .isInstanceOf(PermanentFhirStoreException.class);
        verify(fhirStoreClient, times(1)).executeTransaction(any());
    }

    @Test
    void failsWithoutTouchingStoreWhenBundleIsMalformed() {
        when(reader.read(any(), any())).thenReturn("{\"resourceType\":\"Patient\"}");

        assertThatThrownBy(() -> service.ingest("bucket", "patient.json"))
                .isInstanceOf(BundleParseException.class);
        verify(fhirStoreClient, times(0)).executeTransaction(any());
    }
}

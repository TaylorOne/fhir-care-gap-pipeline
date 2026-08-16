package io.github.taylorone.fhirpipeline.ingestion.fhir;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import io.github.taylorone.fhirpipeline.ingestion.TestResources;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

class BundleTransformerTest {

    private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();

    private final BundleTransformer transformer = new BundleTransformer();

    @Test
    void rewritesSyntheaPostEntriesToIdempotentPuts() {
        Bundle bundle = new BundleParser(FHIR_CONTEXT)
                .parse(TestResources.read("/bundles/synthea-patient-bundle.json"));

        Bundle result = transformer.toIdempotentTransaction(bundle);

        Bundle.BundleEntryComponent patientEntry = result.getEntry().getFirst();
        assertThat(patientEntry.getRequest().getMethod()).isEqualTo(Bundle.HTTPVerb.PUT);
        assertThat(patientEntry.getRequest().getUrl())
                .isEqualTo("Patient/6f7acde5-db81-4361-82cf-886893a3280c");
        assertThat(patientEntry.getResource().getIdPart())
                .isEqualTo("6f7acde5-db81-4361-82cf-886893a3280c");
        // fullUrl is untouched so intra-bundle urn:uuid references still resolve
        assertThat(patientEntry.getFullUrl())
                .isEqualTo("urn:uuid:6f7acde5-db81-4361-82cf-886893a3280c");
        assertThat(result.getEntry())
                .allSatisfy(entry ->
                        assertThat(entry.getRequest().getMethod()).isEqualTo(Bundle.HTTPVerb.PUT));
    }

    @Test
    void normalizesSyntheaBatchToIdempotentTransaction() {
        Bundle batch = new Bundle().setType(Bundle.BundleType.BATCH);
        batch.addEntry()
                .setFullUrl("urn:uuid:11111111-1111-1111-1111-111111111111")
                .setResource(new Patient().setActive(true))
                .getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Patient");

        Bundle result = transformer.toIdempotentTransaction(batch);

        assertThat(result.getType()).isEqualTo(Bundle.BundleType.TRANSACTION);
        Bundle.BundleEntryComponent entry = result.getEntry().getFirst();
        assertThat(entry.getRequest().getMethod()).isEqualTo(Bundle.HTTPVerb.PUT);
        assertThat(entry.getRequest().getUrl())
                .isEqualTo("Patient/11111111-1111-1111-1111-111111111111");
        assertThat(entry.getResource().getIdPart())
                .isEqualTo("11111111-1111-1111-1111-111111111111");
    }

    @Test
    void leavesExistingPutEntriesAlone() {
        Bundle bundle = new Bundle().setType(Bundle.BundleType.TRANSACTION);
        bundle.addEntry()
                .setFullUrl("urn:uuid:11111111-1111-1111-1111-111111111111")
                .setResource(new Patient().setId("explicit-id"))
                .getRequest().setMethod(Bundle.HTTPVerb.PUT).setUrl("Patient/explicit-id");

        Bundle result = transformer.toIdempotentTransaction(bundle);

        Bundle.BundleEntryComponent entry = result.getEntry().getFirst();
        assertThat(entry.getRequest().getUrl()).isEqualTo("Patient/explicit-id");
        assertThat(entry.getResource().getIdPart()).isEqualTo("explicit-id");
    }

    @Test
    void leavesPostWithoutUrnUuidFullUrlAsCreate() {
        Bundle bundle = new Bundle().setType(Bundle.BundleType.TRANSACTION);
        bundle.addEntry()
                .setResource(new Observation())
                .getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Observation");

        Bundle result = transformer.toIdempotentTransaction(bundle);

        assertThat(result.getEntry().getFirst().getRequest().getMethod())
                .isEqualTo(Bundle.HTTPVerb.POST);
    }
}

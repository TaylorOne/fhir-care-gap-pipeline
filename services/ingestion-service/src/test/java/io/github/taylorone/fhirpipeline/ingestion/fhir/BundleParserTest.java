package io.github.taylorone.fhirpipeline.ingestion.fhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.context.FhirContext;
import io.github.taylorone.fhirpipeline.ingestion.TestResources;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;

class BundleParserTest {

    private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();

    private final BundleParser parser = new BundleParser(FHIR_CONTEXT);

    @Test
    void parsesSyntheaTransactionBundle() {
        Bundle bundle = parser.parse(TestResources.read("/bundles/synthea-patient-bundle.json"));

        assertThat(bundle.getType()).isEqualTo(Bundle.BundleType.TRANSACTION);
        assertThat(bundle.getEntry()).hasSize(3);
        assertThat(bundle.getEntry().getFirst().getResource().fhirType()).isEqualTo("Patient");
    }

    @Test
    void rejectsNonBundleResource() {
        String patientJson = """
                {"resourceType": "Patient", "gender": "female"}""";

        assertThatThrownBy(() -> parser.parse(patientJson))
                .isInstanceOf(BundleParseException.class)
                .hasMessageContaining("Patient");
    }

    @Test
    void rejectsNonTransactionBundle() {
        String searchsetJson = """
                {"resourceType": "Bundle", "type": "searchset", "entry": [
                  {"resource": {"resourceType": "Patient"}}
                ]}""";

        assertThatThrownBy(() -> parser.parse(searchsetJson))
                .isInstanceOf(BundleParseException.class)
                .hasMessageContaining("SEARCHSET");
    }

    @Test
    void rejectsEmptyTransactionBundle() {
        String emptyJson = """
                {"resourceType": "Bundle", "type": "transaction"}""";

        assertThatThrownBy(() -> parser.parse(emptyJson))
                .isInstanceOf(BundleParseException.class)
                .hasMessageContaining("no entries");
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> parser.parse("{not json at all"))
                .isInstanceOf(BundleParseException.class);
    }

    @Test
    void strictParsingRejectsUnknownElements() {
        String bogusElement = """
                {"resourceType": "Bundle", "type": "transaction", "entry": [
                  {"resource": {"resourceType": "Patient", "notARealElement": true},
                   "request": {"method": "POST", "url": "Patient"}}
                ]}""";

        assertThatThrownBy(() -> parser.parse(bogusElement))
                .isInstanceOf(BundleParseException.class);
    }
}

package io.github.taylorone.fhirpipeline.gapanalysis.measure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MeasureCatalogTest {

    private final MeasureCatalog catalog = new MeasureCatalog();

    @Test
    void loadsAllThreeMeasuresFromClasspath() {
        assertThat(catalog.measures())
                .extracting(MeasureDefinition::id)
                .containsExactly("BCS_MAMMOGRAPHY", "CDC_A1C", "COL_SCREENING");
    }

    @Test
    void everyMeasureHonorsTheSqlContract() {
        assertThat(catalog.measures()).allSatisfy(measure -> {
            assertThat(measure.sql()).contains("${dataset}");
            assertThat(measure.sql()).contains("@run_date");
            // result contract columns the runner binds by name
            assertThat(measure.sql()).contains("patient_id", "in_numerator", "last_evidence_date");
        });
    }
}

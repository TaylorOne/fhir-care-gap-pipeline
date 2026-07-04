package io.github.taylorone.fhirpipeline.gapanalysis.bigquery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BigQueryMeasureRunnerTest {

    @Test
    void interpolatesDatasetIntoTableReferences() {
        String bound = BigQueryMeasureRunner.bindDataset(
                "SELECT * FROM `${dataset}.Patient`", "my-project.fhir_data");

        assertThat(bound).isEqualTo("SELECT * FROM `my-project.fhir_data.Patient`");
    }

    @Test
    void rejectsDatasetValuesThatCouldAlterQuerySyntax() {
        assertThatThrownBy(() -> BigQueryMeasureRunner.bindDataset(
                "SELECT * FROM `${dataset}.Patient`", "x`; DROP TABLE y"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

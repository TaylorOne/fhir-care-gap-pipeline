package io.github.taylorone.fhirpipeline.gapanalysis.bigquery;

import io.github.taylorone.fhirpipeline.gapanalysis.measure.MeasureDefinition;
import io.github.taylorone.fhirpipeline.gapanalysis.measure.PatientEvaluation;
import java.time.LocalDate;
import java.util.List;

/**
 * Seam for measure evaluation. Production runs SQL on BigQuery; tests fake
 * this without any GCP dependency (there is no credible BigQuery emulator).
 */
public interface MeasureRunner {

    List<PatientEvaluation> evaluate(MeasureDefinition measure, LocalDate runDate);
}

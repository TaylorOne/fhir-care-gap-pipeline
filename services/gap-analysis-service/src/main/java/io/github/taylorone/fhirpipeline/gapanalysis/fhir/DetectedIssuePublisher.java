package io.github.taylorone.fhirpipeline.gapanalysis.fhir;

import io.github.taylorone.fhirpipeline.gapanalysis.measure.MeasureDefinition;
import io.github.taylorone.fhirpipeline.gapanalysis.measure.PatientEvaluation;
import java.time.LocalDate;
import java.util.List;

/**
 * Publishes a measure run's gap results back to the clinical system of
 * record. No-op unless write-back is enabled (see FhirWriteBackConfig).
 */
public interface DetectedIssuePublisher {

    void publish(MeasureDefinition measure, LocalDate runDate, List<PatientEvaluation> evaluations);
}

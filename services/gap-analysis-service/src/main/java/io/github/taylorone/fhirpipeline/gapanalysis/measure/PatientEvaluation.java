package io.github.taylorone.fhirpipeline.gapanalysis.measure;

import java.time.LocalDate;

/**
 * One row of the measure result contract: a denominator patient, whether the
 * numerator was met, and the most recent qualifying service date (null when
 * the numerator was never met).
 */
public record PatientEvaluation(String patientId, boolean inNumerator, LocalDate lastEvidenceDate) {
}

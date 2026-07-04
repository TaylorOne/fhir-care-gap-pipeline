package io.github.taylorone.fhirpipeline.gapanalysis.bigquery;

import io.github.taylorone.fhirpipeline.gapanalysis.measure.MeasureDefinition;
import io.github.taylorone.fhirpipeline.gapanalysis.measure.PatientEvaluation;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local-profile stand-in so the service boots without GCP credentials
 * (Flyway, the push endpoint, and the writer are all locally testable).
 * Evaluating returns no patients rather than fabricating data.
 */
@Component
@Profile("local")
public class LocalMeasureRunner implements MeasureRunner {

    @Override
    public List<PatientEvaluation> evaluate(MeasureDefinition measure, LocalDate runDate) {
        return List.of();
    }
}

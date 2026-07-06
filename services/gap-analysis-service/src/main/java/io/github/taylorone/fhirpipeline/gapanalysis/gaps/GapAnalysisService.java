package io.github.taylorone.fhirpipeline.gapanalysis.gaps;

import io.github.taylorone.fhirpipeline.gapanalysis.bigquery.MeasureRunner;
import io.github.taylorone.fhirpipeline.gapanalysis.fhir.DetectedIssuePublisher;
import io.github.taylorone.fhirpipeline.gapanalysis.measure.MeasureCatalog;
import io.github.taylorone.fhirpipeline.gapanalysis.measure.MeasureDefinition;
import io.github.taylorone.fhirpipeline.gapanalysis.measure.PatientEvaluation;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * One measure run: evaluate every catalog measure against BigQuery as of
 * {@code runDate} and upsert the resulting gap records. The whole run is
 * idempotent (deterministic upserts), so a Pub/Sub redelivery after a partial
 * failure simply re-produces the same rows.
 */
@Service
public class GapAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(GapAnalysisService.class);

    private final MeasureCatalog catalog;
    private final MeasureRunner runner;
    private final CareGapWriter writer;
    private final MeasureRunTracker tracker;
    private final DetectedIssuePublisher issuePublisher;

    public GapAnalysisService(
            MeasureCatalog catalog,
            MeasureRunner runner,
            CareGapWriter writer,
            MeasureRunTracker tracker,
            DetectedIssuePublisher issuePublisher) {
        this.catalog = catalog;
        this.runner = runner;
        this.writer = writer;
        this.tracker = tracker;
        this.issuePublisher = issuePublisher;
    }

    public RunSummary run(LocalDate runDate) {
        UUID runId = tracker.start(runDate);
        MDC.put("measureRunId", runId.toString());
        try {
            int open = 0;
            int closed = 0;
            for (MeasureDefinition measure : catalog.measures()) {
                List<PatientEvaluation> evaluations = runner.evaluate(measure, runDate);
                CareGapWriter.GapCounts counts = writer.upsert(measure.id(), evaluations);
                issuePublisher.publish(measure, runDate, evaluations);
                log.info("Measure {}: {} open, {} closed gaps", measure.id(), counts.open(), counts.closed());
                open += counts.open();
                closed += counts.closed();
            }
            tracker.complete(runId, open, closed);
            log.info("Measure run finished: {} open, {} closed across {} measures",
                    open, closed, catalog.measures().size());
            return new RunSummary(runId, catalog.measures().size(), open, closed);
        } catch (RuntimeException e) {
            tracker.fail(runId, e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            throw e;
        } finally {
            MDC.remove("measureRunId");
        }
    }

    public record RunSummary(UUID runId, int measuresEvaluated, int gapsOpen, int gapsClosed) {
    }
}

package io.github.taylorone.fhirpipeline.gapanalysis.gaps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.taylorone.fhirpipeline.gapanalysis.bigquery.MeasureRunner;
import io.github.taylorone.fhirpipeline.gapanalysis.measure.MeasureCatalog;
import io.github.taylorone.fhirpipeline.gapanalysis.measure.PatientEvaluation;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GapAnalysisServiceTest {

    private static final LocalDate RUN_DATE = LocalDate.of(2026, 7, 1);

    private final MeasureRunner runner = mock(MeasureRunner.class);
    private final CareGapWriter writer = mock(CareGapWriter.class);
    private final MeasureRunTracker tracker = mock(MeasureRunTracker.class);
    private final UUID runId = UUID.randomUUID();

    private GapAnalysisService service;

    @BeforeEach
    void setUp() {
        when(tracker.start(RUN_DATE)).thenReturn(runId);
        // real catalog: the run iterates the actual three measures
        service = new GapAnalysisService(new MeasureCatalog(), runner, writer, tracker);
    }

    @Test
    void evaluatesEveryMeasureAndRecordsAggregateCounts() {
        when(runner.evaluate(any(), eq(RUN_DATE)))
                .thenReturn(List.of(
                        new PatientEvaluation("p1", false, null),
                        new PatientEvaluation("p2", true, LocalDate.of(2026, 3, 1))));
        when(writer.upsert(anyString(), any())).thenReturn(new CareGapWriter.GapCounts(1, 1));

        GapAnalysisService.RunSummary summary = service.run(RUN_DATE);

        assertThat(summary.measuresEvaluated()).isEqualTo(3);
        assertThat(summary.gapsOpen()).isEqualTo(3);   // 1 per measure
        assertThat(summary.gapsClosed()).isEqualTo(3);
        verify(tracker).complete(runId, 3, 3);
    }

    @Test
    void marksRunFailedAndRethrowsWhenAMeasureBlowsUp() {
        when(runner.evaluate(any(), eq(RUN_DATE)))
                .thenThrow(new IllegalStateException("BigQuery unavailable"));

        assertThatThrownBy(() -> service.run(RUN_DATE))
                .isInstanceOf(IllegalStateException.class);

        verify(tracker).fail(runId, "BigQuery unavailable");
        verify(tracker, never()).complete(any(), anyInt(), anyInt());
    }
}

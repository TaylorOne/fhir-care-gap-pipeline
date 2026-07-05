package io.github.taylorone.fhirpipeline.gapanalysis.gaps;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.taylorone.fhirpipeline.gapanalysis.measure.PatientEvaluation;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the real Flyway migrations against a real PostgreSQL and exercises the
 * writer/tracker SQL — the ON CONFLICT upsert semantics this pipeline's
 * idempotency story rests on. Unit tests cannot see this behavior; H2 does not
 * implement PostgreSQL's ON CONFLICT faithfully.
 */
@Testcontainers
class CareGapPersistenceIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static JdbcTemplate jdbc;
    static CareGapWriter writer;
    static MeasureRunTracker tracker;

    @BeforeAll
    static void migrateAndWire() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        writer = new CareGapWriter(jdbc);
        tracker = new MeasureRunTracker(jdbc);
    }

    @BeforeEach
    void cleanGaps() {
        jdbc.update("DELETE FROM care_gap");
    }

    @Test
    void migrationsProduceSchemaWithSeededMeasures() {
        List<String> measureIds = jdbc.queryForList("SELECT id FROM measure ORDER BY id", String.class);

        assertThat(measureIds).containsExactly("BCS_MAMMOGRAPHY", "CDC_A1C", "COL_SCREENING");
    }

    @Test
    void upsertInsertsNewGapsAsOpenOrClosed() {
        CareGapWriter.GapCounts counts = writer.upsert("CDC_A1C", List.of(
                new PatientEvaluation("patient-1", false, null),
                new PatientEvaluation("patient-2", true, LocalDate.of(2026, 3, 10))));

        assertThat(counts).isEqualTo(new CareGapWriter.GapCounts(1, 1));
        Map<String, Object> open = jdbc.queryForMap(
                "SELECT status, last_evidence_date FROM care_gap WHERE patient_id = 'patient-1'");
        assertThat(open.get("status")).isEqualTo("OPEN");
        assertThat(open.get("last_evidence_date")).isNull();
    }

    @Test
    void replayingARunUpdatesInPlaceInsteadOfDuplicating() {
        writer.upsert("CDC_A1C", List.of(new PatientEvaluation("patient-1", false, null)));
        OffsetDateTime firstIdentified = jdbc.queryForObject(
                "SELECT first_identified_at FROM care_gap WHERE patient_id = 'patient-1'",
                OffsetDateTime.class);

        // the same patient closes the gap on a later evaluation
        writer.upsert("CDC_A1C", List.of(
                new PatientEvaluation("patient-1", true, LocalDate.of(2026, 6, 1))));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM care_gap", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM care_gap WHERE patient_id = 'patient-1'", String.class))
                .isEqualTo("CLOSED");
        // provenance survives the update: first_identified_at is not rewritten
        assertThat(jdbc.queryForObject(
                "SELECT first_identified_at FROM care_gap WHERE patient_id = 'patient-1'",
                OffsetDateTime.class))
                .isEqualTo(firstIdentified);
        assertThat(jdbc.queryForObject(
                "SELECT last_evaluated_at FROM care_gap WHERE patient_id = 'patient-1'",
                OffsetDateTime.class))
                .isAfterOrEqualTo(firstIdentified);
    }

    @Test
    void sameGapForDifferentMeasuresAreDistinctRows() {
        writer.upsert("CDC_A1C", List.of(new PatientEvaluation("patient-1", false, null)));
        writer.upsert("COL_SCREENING", List.of(new PatientEvaluation("patient-1", false, null)));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM care_gap", Integer.class)).isEqualTo(2);
    }

    @Test
    void runLifecycleIsRecorded() {
        UUID runId = tracker.start(LocalDate.of(2026, 7, 1));
        tracker.complete(runId, 4, 9);

        Map<String, Object> run = jdbc.queryForMap(
                "SELECT status, gaps_open, gaps_closed, completed_at FROM measure_run WHERE id = ?", runId);
        assertThat(run.get("status")).isEqualTo("SUCCEEDED");
        assertThat(run.get("gaps_open")).isEqualTo(4);
        assertThat(run.get("gaps_closed")).isEqualTo(9);
        assertThat(run.get("completed_at")).isNotNull();

        UUID failedId = tracker.start(LocalDate.of(2026, 7, 2));
        tracker.fail(failedId, "BigQuery unavailable");
        assertThat(jdbc.queryForMap("SELECT status, error FROM measure_run WHERE id = ?", failedId))
                .containsEntry("status", "FAILED")
                .containsEntry("error", "BigQuery unavailable");
    }
}

package io.github.taylorone.fhirpipeline.gapanalysis.gaps;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Records measure-run lifecycle rows so every dashboard number can be traced
 * to the run that produced it, and failed runs are visible operational facts
 * rather than silent log lines.
 */
@Component
public class MeasureRunTracker {

    private final JdbcTemplate jdbcTemplate;

    public MeasureRunTracker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID start(LocalDate runDate) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO measure_run (id, run_date, started_at, status)
                VALUES (?, ?, now(), 'RUNNING')
                """, id, runDate);
        return id;
    }

    public void complete(UUID runId, int gapsOpen, int gapsClosed) {
        jdbcTemplate.update("""
                UPDATE measure_run
                SET status = 'SUCCEEDED', completed_at = now(), gaps_open = ?, gaps_closed = ?
                WHERE id = ?
                """, gapsOpen, gapsClosed, runId);
    }

    public void fail(UUID runId, String error) {
        jdbcTemplate.update("""
                UPDATE measure_run
                SET status = 'FAILED', completed_at = now(), error = left(?, 2000)
                WHERE id = ?
                """, error, runId);
    }
}

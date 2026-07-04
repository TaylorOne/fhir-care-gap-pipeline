package io.github.taylorone.fhirpipeline.gapanalysis.gaps;

import io.github.taylorone.fhirpipeline.gapanalysis.measure.PatientEvaluation;
import java.sql.Date;
import java.sql.Types;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Set-based upserts of gap records. Deliberately plain JDBC, not JPA: the
 * write pattern is "hundreds of rows, one statement shape, conflict-driven" —
 * exactly what {@code INSERT … ON CONFLICT} batching is for. The upsert is
 * deterministic for a given (measure, evaluation) input, which is what makes
 * Pub/Sub's at-least-once delivery safe to replay.
 */
@Component
public class CareGapWriter {

    private static final String UPSERT = """
            INSERT INTO care_gap
              (measure_id, patient_id, status, last_evidence_date, first_identified_at, last_evaluated_at)
            VALUES (?, ?, ?, ?, now(), now())
            ON CONFLICT (measure_id, patient_id) DO UPDATE SET
              status = EXCLUDED.status,
              last_evidence_date = EXCLUDED.last_evidence_date,
              last_evaluated_at = EXCLUDED.last_evaluated_at
            """;

    private final JdbcTemplate jdbcTemplate;

    public CareGapWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @return number of gaps currently open for this measure after the write */
    public GapCounts upsert(String measureId, List<PatientEvaluation> evaluations) {
        jdbcTemplate.batchUpdate(UPSERT, evaluations, 500, (ps, eval) -> {
            ps.setString(1, measureId);
            ps.setString(2, eval.patientId());
            ps.setString(3, eval.inNumerator() ? "CLOSED" : "OPEN");
            if (eval.lastEvidenceDate() == null) {
                ps.setNull(4, Types.DATE);
            } else {
                ps.setDate(4, Date.valueOf(eval.lastEvidenceDate()));
            }
        });
        long open = evaluations.stream().filter(e -> !e.inNumerator()).count();
        return new GapCounts((int) open, evaluations.size() - (int) open);
    }

    public record GapCounts(int open, int closed) {
    }
}

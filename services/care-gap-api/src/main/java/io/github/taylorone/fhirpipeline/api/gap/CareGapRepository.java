package io.github.taylorone.fhirpipeline.api.gap;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CareGapRepository extends JpaRepository<CareGap, Long> {

    /** Optional-filter search; null parameters mean "any". */
    @Query("""
            SELECT g FROM CareGap g
            WHERE (:status IS NULL OR g.status = :status)
              AND (:measureId IS NULL OR g.measureId = :measureId)
              AND (:patientId IS NULL OR g.patientId = :patientId)
            """)
    Page<CareGap> search(
            @Param("status") String status,
            @Param("measureId") String measureId,
            @Param("patientId") String patientId,
            Pageable pageable);

    /** Gap counts per measure and status — the dashboard's headline numbers. */
    @Query("""
            SELECT g.measureId AS measureId, g.status AS status, COUNT(g) AS gaps
            FROM CareGap g
            GROUP BY g.measureId, g.status
            ORDER BY g.measureId, g.status
            """)
    List<SummaryRow> summarize();

    interface SummaryRow {
        String getMeasureId();

        String getStatus();

        long getGaps();
    }
}

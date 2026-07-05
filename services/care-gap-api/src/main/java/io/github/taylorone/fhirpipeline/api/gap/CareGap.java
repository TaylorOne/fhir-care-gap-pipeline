package io.github.taylorone.fhirpipeline.api.gap;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.hibernate.annotations.Immutable;

/**
 * Read-only JPA view of the writer-owned care_gap table.
 * {@code ddl-auto=validate} at startup catches drift between this mapping and
 * the schema the gap-analysis service migrated.
 */
@Entity
@Immutable
@Table(name = "care_gap")
public class CareGap {

    @Id
    private Long id;

    @Column(name = "measure_id")
    private String measureId;

    @Column(name = "patient_id")
    private String patientId;

    private String status;

    @Column(name = "last_evidence_date")
    private LocalDate lastEvidenceDate;

    @Column(name = "first_identified_at")
    private OffsetDateTime firstIdentifiedAt;

    @Column(name = "last_evaluated_at")
    private OffsetDateTime lastEvaluatedAt;

    protected CareGap() {
        // JPA
    }

    public Long getId() {
        return id;
    }

    public String getMeasureId() {
        return measureId;
    }

    public String getPatientId() {
        return patientId;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getLastEvidenceDate() {
        return lastEvidenceDate;
    }

    public OffsetDateTime getFirstIdentifiedAt() {
        return firstIdentifiedAt;
    }

    public OffsetDateTime getLastEvaluatedAt() {
        return lastEvaluatedAt;
    }
}

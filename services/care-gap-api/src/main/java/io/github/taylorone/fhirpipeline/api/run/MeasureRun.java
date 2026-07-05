package io.github.taylorone.fhirpipeline.api.run;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "measure_run")
public class MeasureRun {

    @Id
    private UUID id;

    @Column(name = "run_date")
    private LocalDate runDate;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    private String status;

    private String error;

    @Column(name = "gaps_open")
    private Integer gapsOpen;

    @Column(name = "gaps_closed")
    private Integer gapsClosed;

    protected MeasureRun() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getRunDate() {
        return runDate;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public String getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public Integer getGapsOpen() {
        return gapsOpen;
    }

    public Integer getGapsClosed() {
        return gapsClosed;
    }
}

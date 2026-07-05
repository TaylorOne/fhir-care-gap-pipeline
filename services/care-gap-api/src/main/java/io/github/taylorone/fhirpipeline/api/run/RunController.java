package io.github.taylorone.fhirpipeline.api.run;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Recent measure runs — the dashboard's data-freshness indicator. */
@RestController
public class RunController {

    private final MeasureRunRepository repository;

    public RunController(MeasureRunRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/runs")
    public List<RunDto> recentRuns() {
        return repository.findTop20ByOrderByStartedAtDesc().stream()
                .map(run -> new RunDto(run.getId(), run.getRunDate(), run.getStartedAt(),
                        run.getCompletedAt(), run.getStatus(), run.getError(),
                        run.getGapsOpen(), run.getGapsClosed()))
                .toList();
    }

    public record RunDto(
            UUID id,
            LocalDate runDate,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            String status,
            String error,
            Integer gapsOpen,
            Integer gapsClosed) {
    }
}

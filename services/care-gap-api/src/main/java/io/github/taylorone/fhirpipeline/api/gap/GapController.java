package io.github.taylorone.fhirpipeline.api.gap;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only gap queries. Responses use stable DTO records rather than
 * exposing Spring Data's Page type, whose JSON shape is not a public
 * contract. Sorting is fixed (most recently evaluated first) — the dashboard
 * has no use case for arbitrary sort, and not exposing it keeps the query
 * plan space predictable.
 */
/*
 * Note: no class-level @Validated — Spring Framework 6.1+ validates constrained
 * @RequestParams natively and maps violations to 400 problem details; the AOP
 * variant would intercept first and surface them as 500s.
 */
@RestController
@RequestMapping("/api/gaps")
public class GapController {

    private static final Sort SORT = Sort.by(Sort.Direction.DESC, "lastEvaluatedAt", "id");

    private final CareGapRepository repository;

    public GapController(CareGapRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public PageResponse<GapDto> list(
            @RequestParam(required = false) @Pattern(regexp = "OPEN|CLOSED") String status,
            @RequestParam(required = false) String measureId,
            @RequestParam(required = false) String patientId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        Page<CareGap> gaps = repository.search(
                status, measureId, patientId, PageRequest.of(page, size, SORT));
        return new PageResponse<>(
                gaps.getContent().stream().map(GapDto::from).toList(),
                gaps.getNumber(), gaps.getSize(), gaps.getTotalElements(), gaps.getTotalPages());
    }

    @GetMapping("/summary")
    public List<MeasureSummaryDto> summary() {
        return repository.summarize().stream()
                .map(row -> new MeasureSummaryDto(row.getMeasureId(), row.getStatus(), row.getGaps()))
                .toList();
    }

    public record GapDto(
            Long id,
            String measureId,
            String patientId,
            String status,
            LocalDate lastEvidenceDate,
            OffsetDateTime firstIdentifiedAt,
            OffsetDateTime lastEvaluatedAt) {

        static GapDto from(CareGap gap) {
            return new GapDto(gap.getId(), gap.getMeasureId(), gap.getPatientId(), gap.getStatus(),
                    gap.getLastEvidenceDate(), gap.getFirstIdentifiedAt(), gap.getLastEvaluatedAt());
        }
    }

    public record MeasureSummaryDto(String measureId, String status, long gaps) {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }
}

package io.github.taylorone.fhirpipeline.api.measure;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeasureController {

    private final MeasureRepository repository;

    public MeasureController(MeasureRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/measures")
    public List<MeasureDto> measures() {
        return repository.findAll(Sort.by("id")).stream()
                .map(m -> new MeasureDto(m.getId(), m.getDisplayName()))
                .toList();
    }

    public record MeasureDto(String id, String displayName) {
    }
}

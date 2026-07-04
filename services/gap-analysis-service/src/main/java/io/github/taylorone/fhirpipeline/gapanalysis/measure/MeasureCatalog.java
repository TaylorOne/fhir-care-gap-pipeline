package io.github.taylorone.fhirpipeline.gapanalysis.measure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The measures this service evaluates. SQL is authored in the repo-root
 * {@code measures/} directory (packaged onto the classpath at build time) so
 * adding a measure is a SQL file, a catalog entry, and a seed row — no logic
 * changes. Loading happens eagerly at startup so a missing or unreadable file
 * fails deployment, not the first nightly run.
 */
@Component
public class MeasureCatalog {

    private static final Map<String, String> MEASURE_FILES = Map.of(
            "CDC_A1C", "measures/cdc-a1c.sql",
            "BCS_MAMMOGRAPHY", "measures/bcs-mammography.sql",
            "COL_SCREENING", "measures/col-colorectal.sql");

    private static final Map<String, String> DISPLAY_NAMES = Map.of(
            "CDC_A1C", "Diabetes: HbA1c testing",
            "BCS_MAMMOGRAPHY", "Breast cancer screening",
            "COL_SCREENING", "Colorectal cancer screening");

    private final List<MeasureDefinition> measures;

    public MeasureCatalog() {
        this.measures = MEASURE_FILES.entrySet().stream()
                .map(entry -> new MeasureDefinition(
                        entry.getKey(), DISPLAY_NAMES.get(entry.getKey()), load(entry.getValue())))
                .sorted(java.util.Comparator.comparing(MeasureDefinition::id))
                .toList();
    }

    private static String load(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Measure SQL missing from classpath: " + path, e);
        }
    }

    public List<MeasureDefinition> measures() {
        return measures;
    }
}

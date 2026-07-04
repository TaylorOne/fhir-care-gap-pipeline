package io.github.taylorone.fhirpipeline.gapanalysis.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param bigqueryProject project holding the FHIR export dataset; blank means
 *                        the BigQuery client's default (ADC) project
 * @param bigqueryDataset dataset the FHIR store streams into. The pattern is a
 *                        hard guard: this value is interpolated into measure
 *                        SQL (table names cannot be bound parameters), so it
 *                        must never contain anything query-syntax-relevant.
 */
@Validated
@ConfigurationProperties(prefix = "gapanalysis")
public record GapAnalysisProperties(
        @DefaultValue("") @Pattern(regexp = "[A-Za-z0-9_.-]*") String bigqueryProject,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_]+") String bigqueryDataset) {

    /** The `project.dataset` prefix used in measure SQL table references. */
    public String qualifiedDataset() {
        return bigqueryProject.isBlank() ? bigqueryDataset : bigqueryProject + "." + bigqueryDataset;
    }
}

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
 * @param fhirWritebackEnabled when true, open/closed gaps are also published to
 *                        the FHIR store as DetectedIssue resources
 * @param fhirStoreUrl    FHIR R4 base URL for write-back (required when enabled)
 * @param useGoogleAuth   attach ADC bearer tokens to FHIR requests (off for the
 *                        local HAPI stand-in)
 */
@Validated
@ConfigurationProperties(prefix = "gapanalysis")
public record GapAnalysisProperties(
        @DefaultValue("") @Pattern(regexp = "[A-Za-z0-9_.-]*") String bigqueryProject,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_]+") String bigqueryDataset,
        @DefaultValue("false") boolean fhirWritebackEnabled,
        @DefaultValue("") String fhirStoreUrl,
        @DefaultValue("true") boolean useGoogleAuth) {

    /** The `project.dataset` prefix used in measure SQL table references. */
    public String qualifiedDataset() {
        return bigqueryProject.isBlank() ? bigqueryDataset : bigqueryProject + "." + bigqueryDataset;
    }
}

package io.github.taylorone.fhirpipeline.ingestion.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Externalized configuration for the ingestion service. Every value is supplied
 * through environment variables (see application.yml); the service fails fast at
 * startup if the FHIR store URL is missing rather than failing on first request.
 *
 * @param fhirStoreUrl   FHIR R4 base URL. For the Healthcare API this is
 *                       {@code https://healthcare.googleapis.com/v1/projects/../fhirStores/../fhir};
 *                       locally it points at the HAPI stand-in container.
 * @param useGoogleAuth  whether to attach Application Default Credentials bearer
 *                       tokens to FHIR store requests (off for the local stand-in)
 * @param expectedBucket if non-blank, events for any other bucket are ignored —
 *                       a guard against misconfigured Eventarc triggers
 * @param maxAttempts    total attempts (first try + retries) for transient FHIR
 *                       store failures
 * @param initialBackoff first retry delay; doubles per attempt with jitter
 * @param maxBackoff     upper bound for a single retry delay
 * @param localBundleDir base directory the local-profile object reader resolves
 *                       object names against
 */
@Validated
@ConfigurationProperties(prefix = "ingestion")
public record IngestionProperties(
        @NotBlank String fhirStoreUrl,
        @DefaultValue("true") boolean useGoogleAuth,
        @DefaultValue("") String expectedBucket,
        @Min(1) @DefaultValue("5") int maxAttempts,
        @DefaultValue("500ms") Duration initialBackoff,
        @DefaultValue("8s") Duration maxBackoff,
        @DefaultValue("./bundles") String localBundleDir) {
}

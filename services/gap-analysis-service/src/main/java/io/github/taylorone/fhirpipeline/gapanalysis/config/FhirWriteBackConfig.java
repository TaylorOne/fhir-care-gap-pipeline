package io.github.taylorone.fhirpipeline.gapanalysis.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import io.github.taylorone.fhirpipeline.gapanalysis.fhir.DetectedIssuePublisher;
import io.github.taylorone.fhirpipeline.gapanalysis.fhir.FhirDetectedIssuePublisher;
import io.github.taylorone.fhirpipeline.gapanalysis.fhir.FhirStoreClient;
import io.github.taylorone.fhirpipeline.gapanalysis.fhir.GoogleAdcAuthInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/**
 * FHIR write-back wiring, behind a feature flag: the service runs unchanged
 * where no FHIR store is configured (e.g. unit tests, a BigQuery-only
 * deployment). Failure semantics are deliberate: a write-back error fails the
 * whole measure run, which Pub/Sub then redelivers — safe because both the
 * gap upserts and the DetectedIssue PUTs are idempotent.
 */
@Configuration
public class FhirWriteBackConfig {

    private static final Logger log = LoggerFactory.getLogger(FhirWriteBackConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "gapanalysis", name = "fhir-writeback-enabled", havingValue = "true")
    public DetectedIssuePublisher fhirDetectedIssuePublisher(GapAnalysisProperties properties) {
        Assert.hasText(properties.fhirStoreUrl(),
                "gapanalysis.fhir-store-url is required when fhir-writeback-enabled=true");
        FhirContext fhirContext = FhirContext.forR4();
        fhirContext.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        fhirContext.getRestfulClientFactory().setConnectTimeout(10_000);
        fhirContext.getRestfulClientFactory().setSocketTimeout(120_000);

        IGenericClient client = fhirContext.newRestfulGenericClient(properties.fhirStoreUrl());
        if (properties.useGoogleAuth()) {
            client.registerInterceptor(GoogleAdcAuthInterceptor.fromApplicationDefault());
        }
        log.info("FHIR write-back enabled, targeting {}", properties.fhirStoreUrl());

        FhirStoreClient storeClient = bundle -> client.transaction().withBundle(bundle).execute();
        return new FhirDetectedIssuePublisher(storeClient);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gapanalysis", name = "fhir-writeback-enabled",
            havingValue = "false", matchIfMissing = true)
    public DetectedIssuePublisher noOpDetectedIssuePublisher() {
        log.info("FHIR write-back disabled; gaps are persisted to PostgreSQL only");
        return (measure, runDate, evaluations) -> {
        };
    }
}

package io.github.taylorone.fhirpipeline.ingestion.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import io.github.taylorone.fhirpipeline.ingestion.fhir.GoogleAdcAuthInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the HAPI FHIR client. One deliberate property of this setup: the same
 * {@link IGenericClient} works against both the Google Cloud Healthcare API and
 * the local HAPI FHIR container, because both expose the standard FHIR R4 REST
 * API — only the base URL and the auth interceptor differ.
 */
@Configuration
public class FhirClientConfig {

    private static final Logger log = LoggerFactory.getLogger(FhirClientConfig.class);

    /**
     * FhirContext is expensive to create (it introspects the whole R4 model) and
     * thread-safe; HAPI's documented pattern is one instance per application.
     */
    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    @Bean
    public IGenericClient fhirClient(FhirContext fhirContext, IngestionProperties properties) {
        // Never fetch /metadata before requests: the capability handshake adds a
        // round trip per client and provides nothing we act on.
        fhirContext.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
        fhirContext.getRestfulClientFactory().setConnectTimeout(10_000);
        // Large Synthea transaction bundles can take a while server-side.
        fhirContext.getRestfulClientFactory().setSocketTimeout(120_000);

        IGenericClient client = fhirContext.newRestfulGenericClient(properties.fhirStoreUrl());
        if (properties.useGoogleAuth()) {
            client.registerInterceptor(GoogleAdcAuthInterceptor.fromApplicationDefault());
            log.info("FHIR client targeting {} with Google ADC auth", properties.fhirStoreUrl());
        } else {
            log.info("FHIR client targeting {} without auth (local profile)", properties.fhirStoreUrl());
        }
        return client;
    }
}

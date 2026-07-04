package io.github.taylorone.fhirpipeline.ingestion.config;

import io.github.taylorone.fhirpipeline.ingestion.fhir.TransientFhirStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;

/**
 * Retry policy for FHIR store writes. Only {@link TransientFhirStoreException}
 * (429, 5xx, connection failures) is retried; permanent failures — malformed
 * bundles, 4xx rejections — propagate immediately so poison input never burns
 * retry budget. Backoff is exponential with jitter to avoid thundering-herd
 * retries when many bundle events fail against the same briefly-unavailable
 * store.
 */
@Configuration
public class RetryConfig {

    private static final Logger log = LoggerFactory.getLogger(RetryConfig.class);

    @Bean
    public RetryTemplate fhirRetryTemplate(IngestionProperties properties) {
        return RetryTemplate.builder()
                .maxAttempts(properties.maxAttempts())
                .exponentialBackoff(
                        properties.initialBackoff().toMillis(),
                        2.0,
                        properties.maxBackoff().toMillis(),
                        true) // withRandom = jitter
                .retryOn(TransientFhirStoreException.class)
                .withListener(new RetryListener() {
                    @Override
                    public <T, E extends Throwable> void onError(
                            RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                        log.warn("FHIR store attempt {} failed: {}",
                                context.getRetryCount(), throwable.getMessage());
                    }
                })
                .build();
    }
}

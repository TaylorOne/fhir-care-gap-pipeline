package io.github.taylorone.fhirpipeline.gapanalysis.config;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * BigQuery client via Application Default Credentials. Excluded from the local
 * profile: there is no BigQuery emulator worth trusting, so locally the
 * measure runner seam is exercised by tests, and the SQL itself is validated
 * against the real dev dataset (see REPOSITORY_DESIGN.md §7).
 */
@Configuration
@Profile("!local")
public class BigQueryConfig {

    @Bean
    public BigQuery bigQuery(GapAnalysisProperties properties) {
        BigQueryOptions.Builder builder = BigQueryOptions.newBuilder();
        if (!properties.bigqueryProject().isBlank()) {
            builder.setProjectId(properties.bigqueryProject());
        }
        return builder.build().getService();
    }
}

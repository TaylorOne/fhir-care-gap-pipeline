package io.github.taylorone.fhirpipeline.ingestion.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * The GCS client is only wired outside the local profile so the service can run
 * on a laptop with no GCP credentials at all. Authentication uses Application
 * Default Credentials — the Cloud Run service account at runtime; no key files.
 */
@Configuration
@Profile("!local")
public class GcsConfig {

    @Bean
    public Storage storage() {
        return StorageOptions.getDefaultInstance().getService();
    }
}

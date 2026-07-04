package io.github.taylorone.fhirpipeline.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.taylorone.fhirpipeline.ingestion.ingest.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the full context under the local profile — no GCP credentials needed —
 * to catch wiring mistakes (missing beans, invalid config binding) that unit
 * tests cannot see.
 */
@SpringBootTest
@ActiveProfiles("local")
class IngestionApplicationTest {

    @Autowired
    private IngestionService ingestionService;

    @Test
    void contextLoadsWithLocalProfile() {
        assertThat(ingestionService).isNotNull();
    }
}

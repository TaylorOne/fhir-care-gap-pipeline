package io.github.taylorone.fhirpipeline.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The schema-as-contract test. Applies the WRITER's Flyway migrations (from
 * gap-analysis-service, referenced by filesystem path — deliberately not a
 * copy that could drift), then boots this API against the result. Startup
 * itself asserts the contract via ddl-auto=validate; the HTTP assertions
 * exercise filtering, paging, and aggregation over seeded rows.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CareGapApiIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeAll
    static void migrateWithWritersMigrationsAndSeed() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("filesystem:../gap-analysis-service/src/main/resources/db/migration")
                .placeholders(Map.of("apiuser", postgres.getUsername()))
                .load()
                .migrate();

        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        jdbc.update("""
                INSERT INTO care_gap (measure_id, patient_id, status, last_evidence_date,
                                      first_identified_at, last_evaluated_at)
                VALUES ('CDC_A1C', 'patient-1', 'OPEN',   NULL,         now(), now()),
                       ('CDC_A1C', 'patient-2', 'CLOSED', '2026-03-10', now(), now()),
                       ('COL_SCREENING', 'patient-1', 'OPEN', NULL,     now(), now())
                """);
        jdbc.update("""
                INSERT INTO measure_run (id, run_date, started_at, completed_at, status, gaps_open, gaps_closed)
                VALUES ('11111111-1111-1111-1111-111111111111', '2026-07-01', now(), now(), 'SUCCEEDED', 2, 1)
                """);
    }

    @Autowired
    private TestRestTemplate rest;

    @Test
    void listsGapsFilteredByStatusAndMeasure() {
        ResponseEntity<String> response =
                rest.getForEntity("/api/gaps?status=OPEN&measureId=CDC_A1C", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"patient-1\"")
                .doesNotContain("\"patient-2\"")
                .contains("\"totalElements\":1");
    }

    @Test
    void paginates() {
        ResponseEntity<String> response = rest.getForEntity("/api/gaps?size=2", String.class);

        assertThat(response.getBody())
                .contains("\"totalElements\":3")
                .contains("\"totalPages\":2")
                .contains("\"size\":2");
    }

    @Test
    void rejectsInvalidStatusWithProblemDetail() {
        ResponseEntity<String> response = rest.getForEntity("/api/gaps?status=BOGUS", String.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void summarizesGapsByMeasureAndStatus() {
        ResponseEntity<String> response = rest.getForEntity("/api/gaps/summary", String.class);

        assertThat(response.getBody())
                .contains("\"measureId\":\"CDC_A1C\",\"status\":\"CLOSED\",\"gaps\":1")
                .contains("\"measureId\":\"CDC_A1C\",\"status\":\"OPEN\",\"gaps\":1")
                .contains("\"measureId\":\"COL_SCREENING\",\"status\":\"OPEN\",\"gaps\":1");
    }

    @Test
    void listsSeededMeasureCatalog() {
        ResponseEntity<String> response = rest.getForEntity("/api/measures", String.class);

        assertThat(response.getBody())
                .contains("BCS_MAMMOGRAPHY")
                .contains("Colorectal cancer screening");
    }

    @Test
    void exposesRecentRuns() {
        ResponseEntity<String> response = rest.getForEntity("/api/runs", String.class);

        assertThat(response.getBody())
                .contains("11111111-1111-1111-1111-111111111111")
                .contains("\"status\":\"SUCCEEDED\"")
                .contains("\"gapsOpen\":2");
    }
}

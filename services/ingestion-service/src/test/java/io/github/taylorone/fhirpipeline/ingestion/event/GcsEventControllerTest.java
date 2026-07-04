package io.github.taylorone.fhirpipeline.ingestion.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.taylorone.fhirpipeline.ingestion.config.IngestionProperties;
import io.github.taylorone.fhirpipeline.ingestion.fhir.BundleParseException;
import io.github.taylorone.fhirpipeline.ingestion.fhir.TransientFhirStoreException;
import io.github.taylorone.fhirpipeline.ingestion.ingest.IngestionResult;
import io.github.taylorone.fhirpipeline.ingestion.ingest.IngestionService;
import java.time.Duration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GcsEventController.class)
class GcsEventControllerTest {

    private static final String EVENT_JSON = """
            {"bucket": "ingest-bucket", "name": "patient-1.json",
             "generation": "1", "size": "1024", "contentType": "application/json"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestionService ingestionService;

    @TestConfiguration
    static class PropertiesConfig {
        @Bean
        IngestionProperties ingestionProperties() {
            return new IngestionProperties(
                    "http://localhost:8090/fhir", false, "ingest-bucket",
                    3, Duration.ofMillis(1), Duration.ofMillis(10), "./bundles");
        }
    }

    @Test
    void ingestsFinalizedJsonObject() throws Exception {
        when(ingestionService.ingest("ingest-bucket", "patient-1.json"))
                .thenReturn(new IngestionResult("ingest-bucket", "patient-1.json", 42));

        mockMvc.perform(post("/")
                        .header("ce-type", "google.cloud.storage.object.v1.finalized")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVENT_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("42 resources")));
    }

    @Test
    void ignoresOtherEventTypes() throws Exception {
        mockMvc.perform(post("/")
                        .header("ce-type", "google.cloud.storage.object.v1.deleted")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVENT_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("ignored")));

        verifyNoInteractions(ingestionService);
    }

    @Test
    void ignoresNonJsonObjects() throws Exception {
        mockMvc.perform(post("/")
                        .header("ce-type", "google.cloud.storage.object.v1.finalized")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bucket": "ingest-bucket", "name": "notes.txt"}"""))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("ignored")));

        verifyNoInteractions(ingestionService);
    }

    @Test
    void ignoresUnexpectedBuckets() throws Exception {
        mockMvc.perform(post("/")
                        .header("ce-type", "google.cloud.storage.object.v1.finalized")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bucket": "someone-elses-bucket", "name": "patient-1.json"}"""))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("ignored")));

        verifyNoInteractions(ingestionService);
    }

    @Test
    void acknowledgesPermanentFailuresSoTheyAreNotRedelivered() throws Exception {
        when(ingestionService.ingest(any(), any()))
                .thenThrow(new BundleParseException("not a bundle"));

        mockMvc.perform(post("/")
                        .header("ce-type", "google.cloud.storage.object.v1.finalized")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVENT_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("skipped")));
    }

    @Test
    void requestsRedeliveryForExhaustedTransientFailures() throws Exception {
        when(ingestionService.ingest(any(), any()))
                .thenThrow(new TransientFhirStoreException("store down", null));

        mockMvc.perform(post("/")
                        .header("ce-type", "google.cloud.storage.object.v1.finalized")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVENT_JSON))
                .andExpect(status().isServiceUnavailable());

        verify(ingestionService).ingest("ingest-bucket", "patient-1.json");
    }
}

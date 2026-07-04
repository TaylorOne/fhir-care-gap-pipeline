package io.github.taylorone.fhirpipeline.gapanalysis.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.taylorone.fhirpipeline.gapanalysis.gaps.GapAnalysisService;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pub/Sub push target for {@code measure-run-requested}. The message data may
 * carry {@code {"runDate":"YYYY-MM-DD"}} for backfills; an empty payload means
 * "as of today", which is what the nightly Cloud Scheduler message sends.
 *
 * <p>Status codes are the ack protocol: 2xx acks; a malformed message is acked
 * with a warning (redelivery cannot fix it); any run failure returns 500 so
 * Pub/Sub redelivers with backoff — safe because runs are idempotent.
 */
@RestController
public class MeasureRunController {

    private static final Logger log = LoggerFactory.getLogger(MeasureRunController.class);

    private final GapAnalysisService gapAnalysisService;
    private final ObjectMapper objectMapper;

    public MeasureRunController(GapAnalysisService gapAnalysisService, ObjectMapper objectMapper) {
        this.gapAnalysisService = gapAnalysisService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/")
    public ResponseEntity<String> handle(@RequestBody PushEnvelope envelope) {
        LocalDate runDate;
        try {
            runDate = extractRunDate(envelope);
        } catch (IllegalArgumentException | java.time.DateTimeException | IOException e) {
            log.warn("Acking malformed measure-run message: {}", e.getMessage());
            return ResponseEntity.ok("ignored: malformed message");
        }

        try {
            GapAnalysisService.RunSummary summary = gapAnalysisService.run(runDate);
            return ResponseEntity.ok("run %s: %d open, %d closed gaps across %d measures".formatted(
                    summary.runId(), summary.gapsOpen(), summary.gapsClosed(), summary.measuresEvaluated()));
        } catch (RuntimeException e) {
            log.error("Measure run for {} failed; requesting redelivery", runDate, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("run failed: " + e.getMessage());
        }
    }

    private LocalDate extractRunDate(PushEnvelope envelope) throws IOException {
        String data = envelope.message() == null ? null : envelope.message().data();
        if (data == null || data.isBlank()) {
            return LocalDate.now();
        }
        String json = new String(Base64.getDecoder().decode(data));
        if (json.isBlank()) {
            return LocalDate.now();
        }
        RunRequest request = objectMapper.readValue(json, RunRequest.class);
        return request.runDate() == null || request.runDate().isBlank()
                ? LocalDate.now()
                : LocalDate.parse(request.runDate());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PushEnvelope(PubSubMessage message, String subscription) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PubSubMessage(String data, String messageId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RunRequest(String runDate) {
    }
}

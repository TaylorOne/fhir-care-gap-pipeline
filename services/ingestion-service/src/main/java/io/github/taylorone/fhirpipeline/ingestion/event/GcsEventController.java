package io.github.taylorone.fhirpipeline.ingestion.event;

import io.github.taylorone.fhirpipeline.ingestion.config.IngestionProperties;
import io.github.taylorone.fhirpipeline.ingestion.fhir.BundleParseException;
import io.github.taylorone.fhirpipeline.ingestion.fhir.PermanentFhirStoreException;
import io.github.taylorone.fhirpipeline.ingestion.fhir.TransientFhirStoreException;
import io.github.taylorone.fhirpipeline.ingestion.ingest.IngestionResult;
import io.github.taylorone.fhirpipeline.ingestion.ingest.IngestionService;
import io.github.taylorone.fhirpipeline.ingestion.storage.BundleReadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP target for the Eventarc Cloud Storage trigger. Eventarc delivers
 * CloudEvents in binary mode: attributes travel as {@code ce-*} headers and
 * the body is the raw storage-object JSON, so plain Spring MVC binding is all
 * that is needed — no CloudEvents SDK dependency.
 *
 * <p>The status code we return is a contract with Eventarc's retry behavior,
 * and getting it right is the difference between self-healing and a poison
 * loop:
 * <ul>
 *   <li><b>2xx</b> — event consumed (including "consumed by deciding to skip
 *       it"). Permanent failures return 200: redelivering a malformed bundle
 *       can never succeed, and a non-2xx would make Eventarc hammer us with it
 *       until the message dead-letters. The file remains in the bucket for
 *       offline diagnosis.</li>
 *   <li><b>503</b> — transient failure with local retries exhausted. Eventarc
 *       redelivers with its own backoff, giving the store minutes-scale time
 *       to recover on top of our seconds-scale retries.</li>
 * </ul>
 */
@RestController
public class GcsEventController {

    private static final Logger log = LoggerFactory.getLogger(GcsEventController.class);
    private static final String OBJECT_FINALIZED = "google.cloud.storage.object.v1.finalized";

    private final IngestionService ingestionService;
    private final IngestionProperties properties;

    public GcsEventController(IngestionService ingestionService, IngestionProperties properties) {
        this.ingestionService = ingestionService;
        this.properties = properties;
    }

    @PostMapping("/")
    public ResponseEntity<String> handleEvent(
            @RequestHeader(value = "ce-type", required = false) String eventType,
            @RequestBody GcsObjectEvent event) {

        if (eventType != null && !OBJECT_FINALIZED.equals(eventType)) {
            log.info("Ignoring event of type {}", eventType);
            return ResponseEntity.ok("ignored: unexpected event type");
        }
        if (event.bucket() == null || event.name() == null) {
            log.warn("Ignoring malformed event without bucket/name");
            return ResponseEntity.ok("ignored: malformed event");
        }
        if (!properties.expectedBucket().isBlank()
                && !properties.expectedBucket().equals(event.bucket())) {
            log.warn("Ignoring event for unexpected bucket {}", event.bucket());
            return ResponseEntity.ok("ignored: unexpected bucket");
        }
        if (!event.name().endsWith(".json")) {
            log.info("Ignoring non-JSON object {}", event.name());
            return ResponseEntity.ok("ignored: not a .json object");
        }

        // Correlation fields for every log line of this ingestion.
        MDC.put("gcsBucket", event.bucket());
        MDC.put("gcsObject", event.name());
        try {
            IngestionResult result = ingestionService.ingest(event.bucket(), event.name());
            return ResponseEntity.ok(
                    "ingested %d resources from %s".formatted(result.resourceCount(), result.objectName()));
        } catch (BundleReadException | BundleParseException | PermanentFhirStoreException e) {
            log.error("Permanent ingestion failure for {}; acknowledging so it is not redelivered",
                    event.name(), e);
            return ResponseEntity.ok("skipped: " + e.getMessage());
        } catch (TransientFhirStoreException e) {
            log.error("Transient failure persisted through retries for {}; requesting redelivery",
                    event.name(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("retry requested: " + e.getMessage());
        } finally {
            MDC.remove("gcsBucket");
            MDC.remove("gcsObject");
        }
    }
}

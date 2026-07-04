package io.github.taylorone.fhirpipeline.ingestion.ingest;

import io.github.taylorone.fhirpipeline.ingestion.fhir.BundleParser;
import io.github.taylorone.fhirpipeline.ingestion.fhir.BundleTransformer;
import io.github.taylorone.fhirpipeline.ingestion.fhir.FhirStoreClient;
import io.github.taylorone.fhirpipeline.ingestion.storage.BundleObjectReader;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

/**
 * Orchestrates one bundle ingestion: read → parse → rewrite for idempotency →
 * execute against the FHIR store under the retry policy. Contains no I/O of
 * its own — every external system sits behind a constructor-injected seam,
 * which is what makes this class trivially unit-testable.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final BundleObjectReader objectReader;
    private final BundleParser bundleParser;
    private final BundleTransformer bundleTransformer;
    private final FhirStoreClient fhirStoreClient;
    private final RetryTemplate retryTemplate;

    public IngestionService(
            BundleObjectReader objectReader,
            BundleParser bundleParser,
            BundleTransformer bundleTransformer,
            FhirStoreClient fhirStoreClient,
            RetryTemplate retryTemplate) {
        this.objectReader = objectReader;
        this.bundleParser = bundleParser;
        this.bundleTransformer = bundleTransformer;
        this.fhirStoreClient = fhirStoreClient;
        this.retryTemplate = retryTemplate;
    }

    public IngestionResult ingest(String bucket, String objectName) {
        log.info("Ingesting gs://{}/{}", bucket, objectName);

        String bundleJson = objectReader.read(bucket, objectName);
        Bundle bundle = bundleParser.parse(bundleJson);
        int entryCount = bundle.getEntry().size();
        Bundle idempotentBundle = bundleTransformer.toIdempotentTransaction(bundle);

        // Only the store call sits inside the retry loop: read and parse
        // failures are permanent, and re-reading GCS on a store hiccup would
        // just add cost.
        retryTemplate.execute(context -> fhirStoreClient.executeTransaction(idempotentBundle));

        log.info("Ingested {} resources from gs://{}/{}", entryCount, bucket, objectName);
        return new IngestionResult(bucket, objectName, entryCount);
    }
}

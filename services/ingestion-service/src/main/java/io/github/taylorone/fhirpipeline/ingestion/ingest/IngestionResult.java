package io.github.taylorone.fhirpipeline.ingestion.ingest;

/** Outcome of a successful bundle ingestion, for logging and the HTTP reply. */
public record IngestionResult(String bucket, String objectName, int resourceCount) {
}

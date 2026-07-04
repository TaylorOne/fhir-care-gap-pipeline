package io.github.taylorone.fhirpipeline.ingestion.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The CloudEvent data payload Eventarc sends for Cloud Storage triggers
 * ({@code google.events.cloud.storage.v1.StorageObjectData}). Only the fields
 * this service acts on are bound; everything else is ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GcsObjectEvent(
        String bucket,
        String name,
        String generation,
        Long size,
        String contentType) {
}

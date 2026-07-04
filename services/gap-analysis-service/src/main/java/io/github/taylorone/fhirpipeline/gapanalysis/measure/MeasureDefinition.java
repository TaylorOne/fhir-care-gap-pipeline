package io.github.taylorone.fhirpipeline.gapanalysis.measure;

/**
 * A quality measure: identity plus the BigQuery SQL implementing it. The id
 * matches the seeded row in the operational {@code measure} table.
 */
public record MeasureDefinition(String id, String displayName, String sql) {
}

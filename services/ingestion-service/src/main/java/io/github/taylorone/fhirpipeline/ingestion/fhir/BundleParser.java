package io.github.taylorone.fhirpipeline.ingestion.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.StrictErrorHandler;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Component;

/**
 * Parses raw JSON into a typed R4 {@link Bundle} and enforces the structural
 * contract this pipeline depends on (a transaction bundle).
 *
 * <p>Design decision: we use HAPI's {@link StrictErrorHandler}, which rejects
 * unknown elements and malformed values at parse time, but we deliberately do
 * <em>not</em> run full profile validation ({@code FhirValidator} with
 * terminology support). The Healthcare API FHIR store performs its own
 * structural validation on write; duplicating deep validation here would double
 * the cost of every bundle for no additional safety. Parse-level strictness is
 * the right pre-flight check: it catches corrupt files before we spend a
 * network round trip on them.
 */
@Component
public class BundleParser {

    private final FhirContext fhirContext;

    public BundleParser(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    public Bundle parse(String bundleJson) {
        IBaseResource resource;
        try {
            // Parsers are cheap and not thread-safe; one per call is HAPI's pattern.
            resource = fhirContext.newJsonParser()
                    .setParserErrorHandler(new StrictErrorHandler())
                    .parseResource(bundleJson);
        } catch (DataFormatException e) {
            throw new BundleParseException("Object is not well-formed FHIR JSON: " + e.getMessage(), e);
        }
        if (!(resource instanceof Bundle bundle)) {
            throw new BundleParseException(
                    "Expected a Bundle but object contains a " + resource.fhirType());
        }
        if (bundle.getType() != Bundle.BundleType.TRANSACTION) {
            throw new BundleParseException(
                    "Expected a transaction bundle but got type " + bundle.getType());
        }
        if (bundle.getEntry().isEmpty()) {
            throw new BundleParseException("Transaction bundle contains no entries");
        }
        return bundle;
    }
}

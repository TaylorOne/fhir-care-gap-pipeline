package io.github.taylorone.fhirpipeline.ingestion.fhir;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Rewrites Synthea-style transaction bundles into an idempotent form.
 *
 * <p>Why this exists: Eventarc delivers GCS events <em>at least once</em>, so
 * the same bundle can be ingested twice. Synthea emits entries as
 * {@code POST} (create) with {@code urn:uuid:} fullUrls, and replaying a POST
 * transaction duplicates every resource in the store. The standard enterprise
 * fix — recommended in the Healthcare API documentation — is to convert each
 * create into an update ({@code PUT ResourceType/id}) using the UUID from the
 * entry's fullUrl as the client-assigned resource id. Updates are naturally
 * idempotent: replaying the bundle overwrites each resource with identical
 * content instead of duplicating it.
 *
 * <p>Intra-bundle {@code urn:uuid:} references keep working because the FHIR
 * transaction spec resolves references against entry fullUrls regardless of
 * the request method. The FHIR store must allow update-as-create
 * ({@code enableUpdateCreate} on the Healthcare API store; HAPI allows
 * client-assigned alphanumeric ids by default).
 */
@Component
public class BundleTransformer {

    private static final Logger log = LoggerFactory.getLogger(BundleTransformer.class);
    private static final String URN_UUID_PREFIX = "urn:uuid:";

    /**
     * Mutates the given bundle in place (parsing already produced a private
     * copy; a defensive deep copy would double memory for no benefit) and
     * returns it for fluent use.
     */
    public Bundle toIdempotentTransaction(Bundle bundle) {
        if (bundle.getType() == Bundle.BundleType.BATCH) {
            // Synthea's provider/organization directory exports are batches.
            // Normalize them so the complete directory is committed atomically
            // and the store client has one consistent execute-transaction contract.
            bundle.setType(Bundle.BundleType.TRANSACTION);
            log.info("Normalized Synthea batch bundle to transaction");
        }

        int rewritten = 0;
        int leftAsIs = 0;
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            if (!entry.hasResource() || !entry.hasRequest()) {
                leftAsIs++;
                continue;
            }
            boolean isPost = entry.getRequest().getMethod() == Bundle.HTTPVerb.POST;
            String fullUrl = entry.getFullUrl();
            if (!isPost || fullUrl == null || !fullUrl.startsWith(URN_UUID_PREFIX)) {
                leftAsIs++;
                continue;
            }
            String id = fullUrl.substring(URN_UUID_PREFIX.length());
            Resource resource = entry.getResource();
            resource.setId(id);
            entry.getRequest()
                    .setMethod(Bundle.HTTPVerb.PUT)
                    .setUrl(resource.fhirType() + "/" + id);
            rewritten++;
        }
        if (leftAsIs > 0) {
            log.debug("Left {} entries unmodified (non-POST or no urn:uuid fullUrl)", leftAsIs);
        }
        log.info("Rewrote {} of {} entries to idempotent PUT", rewritten, bundle.getEntry().size());
        return bundle;
    }
}

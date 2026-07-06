package io.github.taylorone.fhirpipeline.gapanalysis.fhir;

import org.hl7.fhir.r4.model.Bundle;

/** Seam for the FHIR store; tests substitute a fake without HTTP. */
public interface FhirStoreClient {

    Bundle executeTransaction(Bundle bundle);
}

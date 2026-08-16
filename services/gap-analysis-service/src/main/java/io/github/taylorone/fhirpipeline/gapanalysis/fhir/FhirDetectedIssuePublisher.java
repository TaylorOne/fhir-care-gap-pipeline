package io.github.taylorone.fhirpipeline.gapanalysis.fhir;

import io.github.taylorone.fhirpipeline.gapanalysis.measure.MeasureDefinition;
import io.github.taylorone.fhirpipeline.gapanalysis.measure.PatientEvaluation;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DetectedIssue;
import org.hl7.fhir.r4.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Closes the interoperability loop: every evaluated gap becomes a
 * {@link DetectedIssue} in the FHIR store, the same pattern payer/provider
 * gaps-in-care exchange uses (cf. Da Vinci DEQM, simplified).
 *
 * <p>Modeling decisions:
 * <ul>
 *   <li><b>Deterministic ids</b> ({@code caregap-<measure>-<patient>}) make the
 *       write a PUT upsert — replayed runs overwrite instead of duplicating,
 *       the same idempotency contract as ingestion and the gap table.</li>
 *   <li><b>Closure is a mitigation, not a status.</b> R4 DetectedIssue.status
 *       has no "resolved" concept; the faithful encoding of "a qualifying
 *       service satisfied the issue" is a {@code mitigation} entry with the
 *       evidence date, while status stays {@code final}.</li>
 *   <li><b>Whole population every run.</b> At portfolio scale the simplicity
 *       wins; the optimization point (publish only status transitions) is the
 *       gap writer's RETURNING clause, if ever needed.</li>
 * </ul>
 */
public class FhirDetectedIssuePublisher implements DetectedIssuePublisher {

    private static final Logger log = LoggerFactory.getLogger(FhirDetectedIssuePublisher.class);

    /** Local code system naming the measures; not a licensed value set. */
    static final String MEASURE_CODE_SYSTEM =
            "https://github.com/TaylorOne/fhir-care-gap-pipeline/CodeSystem/care-gap-measure";
    private static final int BUNDLE_SIZE = 200;

    private final FhirStoreClient fhirStoreClient;

    public FhirDetectedIssuePublisher(FhirStoreClient fhirStoreClient) {
        this.fhirStoreClient = fhirStoreClient;
    }

    @Override
    public void publish(MeasureDefinition measure, LocalDate runDate, List<PatientEvaluation> evaluations) {
        int published = 0;
        for (int from = 0; from < evaluations.size(); from += BUNDLE_SIZE) {
            List<PatientEvaluation> chunk =
                    evaluations.subList(from, Math.min(from + BUNDLE_SIZE, evaluations.size()));
            Bundle bundle = new Bundle().setType(Bundle.BundleType.TRANSACTION);
            for (PatientEvaluation evaluation : chunk) {
                DetectedIssue issue = toDetectedIssue(measure, runDate, evaluation);
                bundle.addEntry()
                        .setResource(issue)
                        .getRequest()
                        .setMethod(Bundle.HTTPVerb.PUT)
                        .setUrl("DetectedIssue/" + issue.getIdPart());
            }
            fhirStoreClient.executeTransaction(bundle);
            published += chunk.size();
        }
        log.info("Published {} DetectedIssue resources for measure {}", published, measure.id());
    }

    DetectedIssue toDetectedIssue(MeasureDefinition measure, LocalDate runDate, PatientEvaluation evaluation) {
        DetectedIssue issue = new DetectedIssue();
        issue.setId(issueId(measure.id(), evaluation.patientId()));
        issue.setStatus(DetectedIssue.DetectedIssueStatus.FINAL);
        issue.setCode(new CodeableConcept()
                .setText(measure.displayName())
                .addCoding(new org.hl7.fhir.r4.model.Coding(
                        MEASURE_CODE_SYSTEM, measure.id(), measure.displayName())));
        issue.setPatient(new Reference("Patient/" + evaluation.patientId()));
        issue.setIdentified(new DateTimeType(runDate.toString()));
        if (evaluation.inNumerator()) {
            issue.setDetail("Care gap closed: qualifying service on record.");
            DetectedIssue.DetectedIssueMitigationComponent mitigation = issue.addMitigation();
            mitigation.setAction(new CodeableConcept().setText("Qualifying service performed"));
            if (evaluation.lastEvidenceDate() != null) {
                mitigation.setDate(Date.from(
                        evaluation.lastEvidenceDate().atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
        } else {
            issue.setDetail("Open care gap: no qualifying service found in the measurement window.");
        }
        return issue;
    }

    /**
     * FHIR ids allow [A-Za-z0-9.-], max 64 chars. Measure ids use underscores
     * (mapped to dashes); patient ids are Synthea UUIDs, so the budget fits —
     * enforced rather than silently truncated, because truncation collides.
     */
    static String issueId(String measureId, String patientId) {
        String id = "caregap-"
                + measureId.toLowerCase(Locale.ROOT).replace('_', '-')
                + "-"
                + patientId;
        if (id.length() > 64 || !id.matches("[A-Za-z0-9.-]+")) {
            throw new IllegalArgumentException("Cannot form a valid FHIR id from: " + id);
        }
        return id;
    }
}

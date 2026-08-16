package io.github.taylorone.fhirpipeline.gapanalysis.fhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.taylorone.fhirpipeline.gapanalysis.measure.MeasureDefinition;
import io.github.taylorone.fhirpipeline.gapanalysis.measure.PatientEvaluation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DetectedIssue;
import org.junit.jupiter.api.Test;

class FhirDetectedIssuePublisherTest {

    private static final MeasureDefinition MEASURE =
            new MeasureDefinition("CDC_A1C", "Diabetes: HbA1c testing", "SELECT 1");
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 7, 1);
    private static final String PATIENT = "6f7acde5-db81-4361-82cf-886893a3280c";

    private final List<Bundle> sentBundles = new ArrayList<>();
    private final FhirDetectedIssuePublisher publisher = new FhirDetectedIssuePublisher(bundle -> {
        sentBundles.add(bundle);
        return new Bundle().setType(Bundle.BundleType.TRANSACTIONRESPONSE);
    });

    @Test
    void openGapBecomesFinalIssueWithoutMitigation() {
        DetectedIssue issue = publisher.toDetectedIssue(
                MEASURE, RUN_DATE, new PatientEvaluation(PATIENT, false, null));

        assertThat(issue.getIdPart()).isEqualTo("caregap-cdc-a1c-" + PATIENT);
        assertThat(issue.getStatus()).isEqualTo(DetectedIssue.DetectedIssueStatus.FINAL);
        assertThat(issue.getPatient().getReference()).isEqualTo("Patient/" + PATIENT);
        assertThat(issue.getCode().getCodingFirstRep().getSystem())
                .isEqualTo(FhirDetectedIssuePublisher.MEASURE_CODE_SYSTEM);
        assertThat(issue.getCode().getCodingFirstRep().getCode()).isEqualTo("CDC_A1C");
        assertThat(issue.hasMitigation()).isFalse();
        assertThat(issue.getDetail()).contains("Open care gap");
    }

    @Test
    void closedGapCarriesMitigationWithEvidenceDate() {
        DetectedIssue issue = publisher.toDetectedIssue(
                MEASURE, RUN_DATE, new PatientEvaluation(PATIENT, true, LocalDate.of(2026, 3, 10)));

        assertThat(issue.hasMitigation()).isTrue();
        assertThat(issue.getMitigationFirstRep().getDate()).isNotNull();
        assertThat(issue.getDetail()).contains("closed");
    }

    @Test
    void publishesIdempotentPutTransaction() {
        publisher.publish(MEASURE, RUN_DATE, List.of(
                new PatientEvaluation(PATIENT, false, null),
                new PatientEvaluation("2c8d1e6b-4a5f-4b1e-9d3a-7f0c92e51b44", true, LocalDate.of(2026, 1, 5))));

        assertThat(sentBundles).hasSize(1);
        Bundle bundle = sentBundles.getFirst();
        assertThat(bundle.getType()).isEqualTo(Bundle.BundleType.TRANSACTION);
        assertThat(bundle.getEntry()).hasSize(2).allSatisfy(entry -> {
            assertThat(entry.getRequest().getMethod()).isEqualTo(Bundle.HTTPVerb.PUT);
            assertThat(entry.getRequest().getUrl()).startsWith("DetectedIssue/caregap-cdc-a1c-");
        });
    }

    @Test
    void chunksLargePopulationsIntoMultipleTransactions() {
        List<PatientEvaluation> many = IntStream.range(0, 450)
                .mapToObj(i -> new PatientEvaluation("patient-%03d".formatted(i), false, null))
                .toList();

        publisher.publish(MEASURE, RUN_DATE, many);

        assertThat(sentBundles).hasSize(3); // 200 + 200 + 50
        assertThat(sentBundles.get(2).getEntry()).hasSize(50);
    }

    @Test
    void refusesIdsThatWouldBeInvalidFhirIds() {
        assertThatThrownBy(() -> FhirDetectedIssuePublisher.issueId("CDC_A1C", "patient/../etc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FhirDetectedIssuePublisher.issueId("CDC_A1C", "x".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

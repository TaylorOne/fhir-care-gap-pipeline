package io.github.taylorone.fhirpipeline.ingestion.fhir;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises real HTTP against a WireMock FHIR endpoint to verify the failure
 * classification contract: transient statuses map to
 * {@link TransientFhirStoreException}, deterministic rejections to
 * {@link PermanentFhirStoreException}. This is behavior a mock of the HAPI
 * client cannot verify, because it lives in how HAPI surfaces HTTP errors.
 */
class HapiFhirStoreClientTest {

    private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();
    private static WireMockServer wireMock;

    private HapiFhirStoreClient client;

    @BeforeAll
    static void startServer() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopServer() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        FHIR_CONTEXT.getRestfulClientFactory()
                .setServerValidationMode(ServerValidationModeEnum.NEVER);
        IGenericClient genericClient = FHIR_CONTEXT.newRestfulGenericClient(
                "http://localhost:" + wireMock.port() + "/fhir");
        client = new HapiFhirStoreClient(genericClient);
    }

    private static Bundle transactionBundle() {
        Bundle bundle = new Bundle().setType(Bundle.BundleType.TRANSACTION);
        bundle.addEntry()
                .setFullUrl("urn:uuid:6f7acde5-db81-4361-82cf-886893a3280c")
                .setResource(new org.hl7.fhir.r4.model.Patient())
                .getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Patient");
        return bundle;
    }

    @Test
    void returnsTransactionResponseOnSuccess() {
        String responseJson = """
                {"resourceType": "Bundle", "type": "transaction-response",
                 "entry": [{"response": {"status": "201 Created"}}]}""";
        wireMock.stubFor(post(urlEqualTo("/fhir")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/fhir+json")
                .withBody(responseJson)));

        Bundle response = client.executeTransaction(transactionBundle());

        assertThat(response.getType()).isEqualTo(Bundle.BundleType.TRANSACTIONRESPONSE);
    }

    @Test
    void classifies503AsTransient() {
        wireMock.stubFor(post(urlEqualTo("/fhir")).willReturn(aResponse()
                .withStatus(503).withBody("upstream unavailable")));

        assertThatThrownBy(() -> client.executeTransaction(transactionBundle()))
                .isInstanceOf(TransientFhirStoreException.class)
                .hasMessageContaining("503");
    }

    @Test
    void classifies429AsTransient() {
        wireMock.stubFor(post(urlEqualTo("/fhir")).willReturn(aResponse()
                .withStatus(429).withBody("quota exceeded")));

        assertThatThrownBy(() -> client.executeTransaction(transactionBundle()))
                .isInstanceOf(TransientFhirStoreException.class);
    }

    @Test
    void classifies400AsPermanent() {
        String outcome = """
                {"resourceType": "OperationOutcome",
                 "issue": [{"severity": "error", "code": "invalid",
                            "diagnostics": "invalid reference"}]}""";
        wireMock.stubFor(post(urlEqualTo("/fhir")).willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/fhir+json")
                .withBody(outcome)));

        assertThatThrownBy(() -> client.executeTransaction(transactionBundle()))
                .isInstanceOf(PermanentFhirStoreException.class)
                .hasMessageContaining("400");
    }

    @Test
    void classifiesConnectionFailureAsTransient() {
        // Point at a closed port: connection refused, no HTTP exchange at all.
        IGenericClient unreachable = FHIR_CONTEXT.newRestfulGenericClient(
                "http://localhost:1/fhir");
        HapiFhirStoreClient unreachableClient = new HapiFhirStoreClient(unreachable);

        assertThatThrownBy(() -> unreachableClient.executeTransaction(transactionBundle()))
                .isInstanceOf(TransientFhirStoreException.class);
    }
}

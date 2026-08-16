package io.github.taylorone.fhirpipeline.gapanalysis.fhir;

import ca.uhn.fhir.rest.client.api.IClientInterceptor;
import ca.uhn.fhir.rest.client.api.IHttpRequest;
import ca.uhn.fhir.rest.client.api.IHttpResponse;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * ADC bearer tokens for FHIR store requests. Deliberately a copy of the
 * ingestion service's interceptor rather than a shared module: two consumers
 * of ~40 lines do not justify cross-service build coupling
 * (REPOSITORY_DESIGN.md §3); revisit if a third FHIR-writing service appears.
 */
public class GoogleAdcAuthInterceptor implements IClientInterceptor {

    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    private final GoogleCredentials credentials;

    GoogleAdcAuthInterceptor(GoogleCredentials credentials) {
        this.credentials = credentials;
    }

    public static GoogleAdcAuthInterceptor fromApplicationDefault() {
        try {
            return new GoogleAdcAuthInterceptor(
                    GoogleCredentials.getApplicationDefault().createScoped(CLOUD_PLATFORM_SCOPE));
        } catch (IOException e) {
            throw new UncheckedIOException("Application Default Credentials not found", e);
        }
    }

    @Override
    public void interceptRequest(IHttpRequest request) {
        try {
            credentials.refreshIfExpired();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to refresh ADC access token", e);
        }
        request.addHeader("Authorization", "Bearer " + credentials.getAccessToken().getTokenValue());
    }

    @Override
    public void interceptResponse(IHttpResponse response) {
        // Nothing to do on responses.
    }
}

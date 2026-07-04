package io.github.taylorone.fhirpipeline.ingestion.fhir;

import ca.uhn.fhir.rest.client.api.IClientInterceptor;
import ca.uhn.fhir.rest.client.api.IHttpRequest;
import ca.uhn.fhir.rest.client.api.IHttpResponse;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Attaches a Google Application Default Credentials bearer token to every FHIR
 * store request. HAPI's built-in {@code BearerTokenAuthInterceptor} holds a
 * fixed string, which is wrong for GCP where access tokens expire hourly; this
 * interceptor delegates caching and refresh to {@link GoogleCredentials}, which
 * only performs a network call when the cached token is near expiry.
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
            // No ADC available: a deployment error, so fail at startup, not per request.
            throw new UncheckedIOException("Application Default Credentials not found", e);
        }
    }

    @Override
    public void interceptRequest(IHttpRequest request) {
        try {
            credentials.refreshIfExpired();
        } catch (IOException e) {
            // Token endpoint unreachable — transient, worth a retry cycle.
            throw new TransientFhirStoreException("Failed to refresh ADC access token", e);
        }
        request.addHeader("Authorization", "Bearer " + credentials.getAccessToken().getTokenValue());
    }

    @Override
    public void interceptResponse(IHttpResponse response) {
        // Nothing to do on responses.
    }
}

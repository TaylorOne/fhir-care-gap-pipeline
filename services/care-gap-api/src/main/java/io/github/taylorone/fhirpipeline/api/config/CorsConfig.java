package io.github.taylorone.fhirpipeline.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The API is browser-consumed from the dashboard's origin only. Locally
 * (ng serve) that is http://localhost:4200; in GCP it is the dashboard's
 * Cloud Run origin. This is a pattern, not an exact origin: every Cloud Run
 * service answers on both a default hash-based hostname and a deterministic
 * project-number one, and either may end up in a user's browser, so both
 * must match. Read-only API, so only GET is exposed.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String dashboardOriginPattern;

    public CorsConfig(@Value("${api.dashboard-origin-pattern:http://localhost:4200}") String dashboardOriginPattern) {
        this.dashboardOriginPattern = dashboardOriginPattern;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(dashboardOriginPattern)
                .allowedMethods("GET");
    }
}

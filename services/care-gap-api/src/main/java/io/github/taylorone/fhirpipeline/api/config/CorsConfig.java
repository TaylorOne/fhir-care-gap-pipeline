package io.github.taylorone.fhirpipeline.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The API is browser-consumed from the dashboard's origin only. Locally
 * (ng serve) that is http://localhost:4200; in GCP it is the dashboard's CDN
 * origin. Read-only API, so only GET is exposed.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String dashboardOrigin;

    public CorsConfig(@Value("${api.dashboard-origin:http://localhost:4200}") String dashboardOrigin) {
        this.dashboardOrigin = dashboardOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(dashboardOrigin)
                .allowedMethods("GET");
    }
}

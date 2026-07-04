package io.github.taylorone.fhirpipeline.gapanalysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GapAnalysisApplication {

    public static void main(String[] args) {
        SpringApplication.run(GapAnalysisApplication.class, args);
    }
}

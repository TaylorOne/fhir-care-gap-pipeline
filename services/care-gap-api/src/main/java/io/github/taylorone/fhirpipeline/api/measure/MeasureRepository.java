package io.github.taylorone.fhirpipeline.api.measure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MeasureRepository extends JpaRepository<Measure, String> {
}

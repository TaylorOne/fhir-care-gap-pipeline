package io.github.taylorone.fhirpipeline.api.run;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeasureRunRepository extends JpaRepository<MeasureRun, UUID> {

    List<MeasureRun> findTop20ByOrderByStartedAtDesc();
}

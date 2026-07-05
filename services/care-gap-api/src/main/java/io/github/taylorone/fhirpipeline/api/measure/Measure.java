package io.github.taylorone.fhirpipeline.api.measure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "measure")
public class Measure {

    @Id
    private String id;

    @Column(name = "display_name")
    private String displayName;

    protected Measure() {
        // JPA
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }
}

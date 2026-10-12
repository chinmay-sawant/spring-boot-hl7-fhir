package com.example.fhirdemo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "practitioners")
public class PractitionerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String fhirId;

    private String identifier;

    private Boolean active;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String resourceJson;

    public PractitionerEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFhirId() {
        return fhirId;
    }

    public void setFhirId(String fhirId) {
        this.fhirId = fhirId;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getResourceJson() {
        return resourceJson;
    }

    public void setResourceJson(String resourceJson) {
        this.resourceJson = resourceJson;
    }
}

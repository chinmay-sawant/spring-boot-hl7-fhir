package com.example.fhirdemo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "observations")
public class ObservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String fhirId;

    private String patientRef;

    private String loincCode;

    private String display;

    @jakarta.persistence.Column(name = "obs_value")
    private Double value;

    private String unit;

    private String effectiveDate;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String resourceJson;

    public ObservationEntity() {
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

    public String getPatientRef() {
        return patientRef;
    }

    public void setPatientRef(String patientRef) {
        this.patientRef = patientRef;
    }

    public String getLoincCode() {
        return loincCode;
    }

    public void setLoincCode(String loincCode) {
        this.loincCode = loincCode;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(String effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public String getResourceJson() {
        return resourceJson;
    }

    public void setResourceJson(String resourceJson) {
        this.resourceJson = resourceJson;
    }
}

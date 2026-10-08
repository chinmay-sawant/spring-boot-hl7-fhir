package com.example.healthcare.fhir.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import ca.uhn.fhir.context.FhirContext;
import com.example.healthcare.domain.patient.Patient;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

/**
 * Tests for {@link FhirPatientMapper} in both directions, including absent
 * optional fields and a strict check that no nonstandard attributes are
 * emitted.
 */
class FhirPatientMapperTest {

    private static final String SYSTEM = "http://hospital.example.org/mrn";
    private static final String VALUE = "MRN-1001";
    private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();

    @Test
    void mapsDomainPatientToFhirPatient() {
        Patient domain = new Patient(
                SYSTEM, VALUE, "Doe", "John", LocalDate.of(1990, 5, 15), "male", "patient-123");

        org.hl7.fhir.r4.model.Patient fhirPatient = FhirPatientMapper.toFhir(domain);

        assertThat(fhirPatient.getIdElement().getIdPart()).isEqualTo("patient-123");
        assertThat(fhirPatient.getIdentifier()).hasSize(1);
        Identifier identifier = fhirPatient.getIdentifierFirstRep();
        assertThat(identifier.getSystem()).isEqualTo(SYSTEM);
        assertThat(identifier.getValue()).isEqualTo(VALUE);
        assertThat(fhirPatient.getName()).hasSize(1);
        HumanName name = fhirPatient.getNameFirstRep();
        assertThat(name.getFamily()).isEqualTo("Doe");
        assertThat(name.getGivenAsSingleString()).isEqualTo("John");
        assertThat(fhirPatient.getBirthDateElement().asStringValue()).isEqualTo("1990-05-15");
        assertThat(fhirPatient.getGender()).isEqualTo(Enumerations.AdministrativeGender.MALE);
    }

    @Test
    void mapsFhirPatientToDomainPatient() {
        org.hl7.fhir.r4.model.Patient fhirPatient = new org.hl7.fhir.r4.model.Patient();
        fhirPatient.setId("patient-123");
        fhirPatient.addIdentifier(new Identifier().setSystem(SYSTEM).setValue(VALUE));
        HumanName name = fhirPatient.addName();
        name.setFamily("Doe");
        name.addGiven("John");
        fhirPatient.setBirthDateElement(new DateType(1990, 4, 15));
        fhirPatient.setGender(Enumerations.AdministrativeGender.MALE);

        Patient domain = FhirPatientMapper.toDomain(fhirPatient);

        assertThat(domain).isEqualTo(
                new Patient(SYSTEM, VALUE, "Doe", "John", LocalDate.of(1990, 5, 15), "male", "patient-123"));
    }

    @Test
    void leavesAbsentOptionalFieldsAbsent() throws Exception {
        Patient domain = new Patient(SYSTEM, VALUE, "Doe", null, null, null, null);

        org.hl7.fhir.r4.model.Patient fhirPatient = FhirPatientMapper.toFhir(domain);

        assertThat(fhirPatient.hasBirthDate()).isFalse();
        assertThat(fhirPatient.hasGender()).isFalse();
        JSONAssert.assertEquals(
                "{\"resourceType\":\"Patient\","
                        + "\"identifier\":[{\"system\":\"" + SYSTEM + "\",\"value\":\"" + VALUE + "\"}],"
                        + "\"name\":[{\"family\":\"Doe\"}]}",
                FHIR_CONTEXT.newJsonParser().encodeResourceToString(fhirPatient),
                true);

        Patient roundTripped = FhirPatientMapper.toDomain(fhirPatient);

        assertThat(roundTripped.birthDate()).isNull();
        assertThat(roundTripped.gender()).isNull();
        assertThat(roundTripped.givenName()).isNull();
        assertThat(roundTripped.resourceId()).isNull();
    }

    @Test
    void usesOnlyStandardElements() throws Exception {
        Patient domain = new Patient(
                SYSTEM, VALUE, "Doe", "John", LocalDate.of(1990, 5, 15), "male", "patient-123");

        org.hl7.fhir.r4.model.Patient fhirPatient = FhirPatientMapper.toFhir(domain);

        assertThat(fhirPatient.hasExtension()).isFalse();
        assertThat(fhirPatient.getModifierExtension()).isEmpty();
        assertThat(fhirPatient.getTelecom()).isEmpty();
        assertThat(fhirPatient.getAddress()).isEmpty();
        assertThat(fhirPatient.hasActive()).isFalse();
        assertThat(fhirPatient.hasDeceased()).isFalse();
        assertThat(fhirPatient.getPhoto()).isEmpty();
        assertThat(fhirPatient.getContact()).isEmpty();
        assertThat(fhirPatient.getCommunication()).isEmpty();
        assertThat(fhirPatient.getGeneralPractitioner()).isEmpty();
        assertThat(fhirPatient.hasManagingOrganization()).isFalse();
        assertThat(fhirPatient.getLink()).isEmpty();
        assertThat(fhirPatient.hasText()).isFalse();
        assertThat(fhirPatient.hasMeta()).isFalse();
        assertThat(fhirPatient.hasLanguage()).isFalse();
        assertThat(fhirPatient.hasImplicitRules()).isFalse();
        assertThat(fhirPatient.hasContained()).isFalse();
        JSONAssert.assertEquals(
                "{\"resourceType\":\"Patient\",\"id\":\"patient-123\","
                        + "\"identifier\":[{\"system\":\"" + SYSTEM + "\",\"value\":\"" + VALUE + "\"}],"
                        + "\"name\":[{\"family\":\"Doe\",\"given\":[\"John\"]}],"
                        + "\"birthDate\":\"1990-05-15\",\"gender\":\"male\"}",
                FHIR_CONTEXT.newJsonParser().encodeResourceToString(fhirPatient),
                true);
    }
}

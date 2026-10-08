package com.example.healthcare.fhir.mapper;

import java.time.LocalDate;
import java.util.Calendar;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import com.example.healthcare.domain.patient.Patient;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;

/**
 * Maps between the domain {@link Patient} and the FHIR R4 Patient resource.
 *
 * <p>Only the standard FHIR R4 elements {@code id}, {@code identifier},
 * {@code name} (family and given), {@code birthDate}, and {@code gender} are
 * ever populated, as required by the patient vertical slice. Optional fields
 * that are absent in the domain (birth date, gender, or individual identifier,
 * family, and given values) stay absent on the FHIR resource instead of being
 * written as empty or placeholder values.
 *
 * <p>The mapper is stateless and holds no business decisions: the identifier
 * is carried as data, never interpreted as the FHIR resource id.
 */
public final class FhirPatientMapper {

    private FhirPatientMapper() {
    }

    /**
     * Maps a domain patient to a FHIR R4 Patient.
     *
     * @param patient the domain patient; the resource id may be {@code null}
     *     before the first save
     * @return a FHIR Patient carrying only standard elements
     */
    public static org.hl7.fhir.r4.model.Patient toFhir(Patient patient) {
        org.hl7.fhir.r4.model.Patient fhirPatient = new org.hl7.fhir.r4.model.Patient();
        if (patient.resourceId() != null) {
            fhirPatient.setId(patient.resourceId());
        }
        if (patient.identifierSystem() != null || patient.identifierValue() != null) {
            Identifier identifier = fhirPatient.addIdentifier();
            if (patient.identifierSystem() != null) {
                identifier.setSystem(patient.identifierSystem());
            }
            if (patient.identifierValue() != null) {
                identifier.setValue(patient.identifierValue());
            }
        }
        if (patient.familyName() != null || patient.givenName() != null) {
            HumanName name = fhirPatient.addName();
            if (patient.familyName() != null) {
                name.setFamily(patient.familyName());
            }
            if (patient.givenName() != null) {
                name.addGiven(patient.givenName());
            }
        }
        if (patient.birthDate() != null) {
            LocalDate birthDate = patient.birthDate();
            // The int-based DateType constructor builds a GMT date with DAY
            // precision, so the encoded value is exactly yyyy-MM-dd and does
            // not shift with the JVM default time zone.
            fhirPatient.setBirthDateElement(new DateType(
                    birthDate.getYear(), birthDate.getMonthValue() - 1, birthDate.getDayOfMonth()));
        }
        if (patient.gender() != null && !patient.gender().isBlank()) {
            fhirPatient.setGender(toAdministrativeGender(patient.gender()));
        }
        return fhirPatient;
    }

    /**
     * Maps a FHIR R4 Patient to a domain patient.
     *
     * <p>Uses the first {@code identifier} and {@code name} repetition, which
     * is the only repetition this application writes; the resource id is the
     * FHIR logical id, never the identifier value.
     *
     * @param fhirPatient the FHIR Patient, for example from a search bundle
     * @return the domain patient, with {@code resourceId} taken from the
     *     resource id
     * @throws IllegalArgumentException when the birth date is not a full date
     *     or the gender code is not a FHIR administrative gender
     */
    public static Patient toDomain(org.hl7.fhir.r4.model.Patient fhirPatient) {
        String identifierSystem = null;
        String identifierValue = null;
        if (fhirPatient.hasIdentifier()) {
            Identifier identifier = fhirPatient.getIdentifierFirstRep();
            identifierSystem = identifier.getSystem();
            identifierValue = identifier.getValue();
        }
        String familyName = null;
        String givenName = null;
        if (fhirPatient.hasName()) {
            HumanName name = fhirPatient.getNameFirstRep();
            familyName = name.getFamily();
            if (!name.getGiven().isEmpty()) {
                givenName = name.getGivenAsSingleString();
            }
        }
        return new Patient(
                identifierSystem,
                identifierValue,
                familyName,
                givenName,
                toLocalDate(fhirPatient),
                fhirPatient.hasGender() ? fhirPatient.getGender().toCode() : null,
                fhirPatient.getIdElement().getIdPart());
    }

    private static LocalDate toLocalDate(org.hl7.fhir.r4.model.Patient fhirPatient) {
        if (!fhirPatient.hasBirthDate()) {
            return null;
        }
        DateType birthDate = fhirPatient.getBirthDateElement();
        if (birthDate.getPrecision() != TemporalPrecisionEnum.DAY) {
            throw new IllegalArgumentException("FHIR Patient birthDate '" + birthDate.asStringValue()
                    + "' is not a full date (yyyy-MM-dd) and cannot be mapped to the domain patient");
        }
        Calendar calendar = birthDate.getValueAsCalendar();
        return LocalDate.of(
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH));
    }

    private static Enumerations.AdministrativeGender toAdministrativeGender(String gender) {
        try {
            Enumerations.AdministrativeGender administrativeGender = Enumerations.AdministrativeGender.fromCode(gender);
            if (administrativeGender == null) {
                throw new IllegalArgumentException("Unsupported patient gender code: '" + gender + "'");
            }
            return administrativeGender;
        } catch (FHIRException e) {
            throw new IllegalArgumentException("Unsupported patient gender code: '" + gender + "'", e);
        }
    }
}

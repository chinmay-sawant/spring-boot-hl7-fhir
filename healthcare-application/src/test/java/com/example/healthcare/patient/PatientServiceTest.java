package com.example.healthcare.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import com.example.healthcare.domain.patient.Patient;
import com.example.healthcare.domain.patient.PatientRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the patient use case with a mocked repository port. Covers the
 * new, existing, and missing patient paths.
 */
@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    private static final String SYSTEM = "http://hospital.example.org/mrn";
    private static final String VALUE = "MRN-1001";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 5, 15);

    @Mock
    private PatientRepositoryPort patientRepository;

    private PatientService patientService;

    @BeforeEach
    void setUp() {
        patientService = new PatientService(patientRepository);
    }

    @Test
    void registersANewPatientWhenTheIdentifierIsNotYetKnown() {
        when(patientRepository.findByIdentifier(SYSTEM, VALUE)).thenReturn(Optional.empty());
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> withResourceId(invocation.getArgument(0), "pat-123"));

        PatientResponse response = patientService.register(request("Doe", "John"));

        ArgumentCaptor<Patient> saved = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(saved.capture());

        assertThat(saved.getValue().resourceId()).isNull();
        assertThat(saved.getValue().identifierSystem()).isEqualTo(SYSTEM);
        assertThat(saved.getValue().identifierValue()).isEqualTo(VALUE);
        assertThat(saved.getValue().familyName()).isEqualTo("Doe");
        assertThat(saved.getValue().givenName()).isEqualTo("John");
        assertThat(saved.getValue().birthDate()).isEqualTo(BIRTH_DATE);
        assertThat(saved.getValue().gender()).isEqualTo("male");

        assertThat(response.resourceId()).isEqualTo("pat-123");
        assertThat(response.identifierSystem()).isEqualTo(SYSTEM);
        assertThat(response.identifierValue()).isEqualTo(VALUE);
    }

    @Test
    void updatesTheExistingPatientWhenTheIdentifierIsAlreadyRegistered() {
        Patient existing = new Patient(SYSTEM, VALUE, "Smith", "Jane", BIRTH_DATE, "female", "pat-456");
        when(patientRepository.findByIdentifier(SYSTEM, VALUE)).thenReturn(Optional.of(existing));
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PatientResponse response = patientService.register(request("Doe", "John"));

        ArgumentCaptor<Patient> saved = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(saved.capture());

        assertThat(saved.getValue().resourceId()).isEqualTo("pat-456");
        assertThat(saved.getValue().familyName()).isEqualTo("Doe");
        assertThat(saved.getValue().givenName()).isEqualTo("John");
        assertThat(saved.getValue().gender()).isEqualTo("male");

        assertThat(response.resourceId()).isEqualTo("pat-456");
        assertThat(response.identifierValue()).isEqualTo(VALUE);
    }

    @Test
    void retrievesAPatientByResourceId() {
        Patient patient = new Patient(SYSTEM, VALUE, "Doe", "John", BIRTH_DATE, "male", "pat-123");
        when(patientRepository.findById("pat-123")).thenReturn(Optional.of(patient));

        PatientResponse response = patientService.findById("pat-123");

        assertThat(response.resourceId()).isEqualTo("pat-123");
        assertThat(response.identifierSystem()).isEqualTo(SYSTEM);
        assertThat(response.identifierValue()).isEqualTo(VALUE);
    }

    @Test
    void rejectsAnUnknownResourceId() {
        when(patientRepository.findById("pat-404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.findById("pat-404"))
                .isInstanceOf(PatientService.PatientNotFoundException.class)
                .hasMessageContaining("pat-404");
    }

    private static PatientRequest request(String familyName, String givenName) {
        return new PatientRequest(SYSTEM, VALUE, familyName, givenName, BIRTH_DATE, "male");
    }

    private static Patient withResourceId(Patient patient, String resourceId) {
        return new Patient(
                patient.identifierSystem(),
                patient.identifierValue(),
                patient.familyName(),
                patient.givenName(),
                patient.birthDate(),
                patient.gender(),
                resourceId);
    }
}

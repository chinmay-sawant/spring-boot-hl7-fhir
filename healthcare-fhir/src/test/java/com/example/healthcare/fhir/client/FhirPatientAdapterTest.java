package com.example.healthcare.fhir.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ICreate;
import ca.uhn.fhir.rest.gclient.ICreateTyped;
import ca.uhn.fhir.rest.gclient.ICriterion;
import ca.uhn.fhir.rest.gclient.IUpdate;
import ca.uhn.fhir.rest.gclient.IUpdateTyped;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.example.healthcare.domain.patient.Patient;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for {@link FhirPatientAdapter}: identifier search match, no match, and
 * multiple matches; the id read; and both save branches (create and update).
 *
 * <p>The HAPI {@link IGenericClient} is a deep-stub mock of its fluent API, so
 * no HTTP call ever happens.
 */
@ExtendWith(MockitoExtension.class)
class FhirPatientAdapterTest {

    private static final String SYSTEM = "http://hospital.example.org/mrn";
    private static final String VALUE = "MRN-1001";

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private IGenericClient client;

    private FhirPatientAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new FhirPatientAdapter(client);
    }

    @Test
    void findByIdentifierReturnsTheSingleMatch() {
        stubIdentifierSearch(bundleWith(fhirPatient("pat-1")));

        assertThat(adapter.findByIdentifier(SYSTEM, VALUE))
                .contains(patient("pat-1"));
    }

    @Test
    void findByIdentifierReturnsEmptyWhenNothingMatches() {
        stubIdentifierSearch(bundleWith());

        assertThat(adapter.findByIdentifier(SYSTEM, VALUE)).isEmpty();
    }

    @Test
    void findByIdentifierRejectsMultipleMatches() {
        stubIdentifierSearch(bundleWith(fhirPatient("pat-1"), fhirPatient("pat-2")));

        assertThatThrownBy(() -> adapter.findByIdentifier(SYSTEM, VALUE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SYSTEM + "|" + VALUE)
                .hasMessageContaining("2");
    }

    @Test
    void findByIdentifierRejectsMultipleReportedMatchesEvenWhenPagedToOneEntry() {
        Bundle bundle = bundleWith(fhirPatient("pat-1"));
        bundle.setTotal(3);
        stubIdentifierSearch(bundle);

        assertThatThrownBy(() -> adapter.findByIdentifier(SYSTEM, VALUE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3");
    }

    @Test
    void findByIdReadsTheResourceByItsId() {
        when(client.read()
                .resource(org.hl7.fhir.r4.model.Patient.class)
                .withId("pat-9")
                .execute()).thenReturn(fhirPatient("pat-9"));

        assertThat(adapter.findById("pat-9")).contains(patient("pat-9"));
    }

    @Test
    void findByIdReturnsEmptyWhenTheServerReportsNotFound() {
        when(client.read()
                .resource(org.hl7.fhir.r4.model.Patient.class)
                .withId("missing")
                .execute()).thenThrow(new ResourceNotFoundException("Patient/missing is not known"));

        assertThat(adapter.findById("missing")).isEmpty();
    }

    @Test
    void saveCreatesExactlyOneNewPatientWhenNoMatchExists() {
        stubIdentifierSearch(bundleWith());
        ICreate create = mock(ICreate.class);
        ICreateTyped createTyped = mock(ICreateTyped.class);
        AtomicReference<String> clientSideIdOnCreate = new AtomicReference<>();
        when(client.create()).thenReturn(create);
        when(create.resource(any(org.hl7.fhir.r4.model.Patient.class))).thenAnswer(invocation -> {
            org.hl7.fhir.r4.model.Patient resource = invocation.getArgument(0);
            clientSideIdOnCreate.set(resource.getIdElement().getIdPart());
            return createTyped;
        });
        when(createTyped.execute()).thenReturn(new MethodOutcome(new IdType("Patient", "new-1")));

        Patient saved = adapter.save(patient(null));

        assertThat(saved).isEqualTo(patient("new-1"));
        assertThat(clientSideIdOnCreate.get()).isNull();
        ArgumentCaptor<org.hl7.fhir.r4.model.Patient> created =
                ArgumentCaptor.forClass(org.hl7.fhir.r4.model.Patient.class);
        verify(create).resource(created.capture());
        assertThat(created.getValue().getIdentifierFirstRep().getValue()).isEqualTo(VALUE);
        verify(createTyped).execute();
        verify(client, never()).update();
    }

    @Test
    void saveUpdatesTheExistingMatchWhenIdentifierIsKnown() {
        stubIdentifierSearch(bundleWith(fhirPatient("existing-7")));
        IUpdate update = mock(IUpdate.class);
        IUpdateTyped updateTyped = mock(IUpdateTyped.class);
        when(client.update()).thenReturn(update);
        when(update.resource(any(org.hl7.fhir.r4.model.Patient.class))).thenReturn(updateTyped);
        when(updateTyped.execute()).thenReturn(new MethodOutcome(new IdType("Patient", "existing-7")));

        Patient saved = adapter.save(patient(null));

        assertThat(saved).isEqualTo(patient("existing-7"));
        ArgumentCaptor<org.hl7.fhir.r4.model.Patient> updated =
                ArgumentCaptor.forClass(org.hl7.fhir.r4.model.Patient.class);
        verify(update).resource(updated.capture());
        assertThat(updated.getValue().getIdElement().getIdPart()).isEqualTo("existing-7");
        verify(updateTyped).execute();
        verify(client, never()).create();
    }

    @SuppressWarnings("unchecked")
    private void stubIdentifierSearch(Bundle results) {
        when(client.search()
                .forResource(org.hl7.fhir.r4.model.Patient.class)
                .where(any(ICriterion.class))
                .returnBundle(Bundle.class)
                .execute()).thenReturn(results);
    }

    private static Bundle bundleWith(org.hl7.fhir.r4.model.Patient... patients) {
        Bundle bundle = new Bundle();
        bundle.setTotal(patients.length);
        for (org.hl7.fhir.r4.model.Patient patient : patients) {
            bundle.addEntry().setResource(patient);
        }
        return bundle;
    }

    private static org.hl7.fhir.r4.model.Patient fhirPatient(String resourceId) {
        org.hl7.fhir.r4.model.Patient fhirPatient = new org.hl7.fhir.r4.model.Patient();
        fhirPatient.setId(resourceId);
        fhirPatient.addIdentifier(new Identifier().setSystem(SYSTEM).setValue(VALUE));
        HumanName name = fhirPatient.addName();
        name.setFamily("Doe");
        name.addGiven("John");
        fhirPatient.setBirthDateElement(new DateType(1990, 4, 15));
        fhirPatient.setGender(Enumerations.AdministrativeGender.MALE);
        return fhirPatient;
    }

    private static Patient patient(String resourceId) {
        return new Patient(SYSTEM, VALUE, "Doe", "John", LocalDate.of(1990, 5, 15), "male", resourceId);
    }
}

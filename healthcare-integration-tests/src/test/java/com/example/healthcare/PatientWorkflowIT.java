package com.example.healthcare;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import ca.uhn.fhir.context.FhirContext;
import com.example.healthcare.patient.PatientRequest;
import com.example.healthcare.patient.PatientResponse;
import com.example.healthcare.support.AbstractIntegrationTest;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end patient workflow through the REST API, the in-memory
 * application database, and the in-memory FHIR R4 server (plan row 6.4 of
 * {@code plans/spring-boot/01-foundation.md}).
 *
 * <p>Proves milestone M2: a synthetic patient is registered through
 * {@code POST /api/v1/patients} (201), retrieved through
 * {@code GET /api/v1/patients/{id}} (200, matching identifier), the FHIR
 * server holds exactly one {@code Patient} for the business identifier, and
 * registering the same identifier again still leaves exactly one.
 */
class PatientWorkflowIT extends AbstractIntegrationTest {

    private static final String IDENTIFIER_SYSTEM = "http://hospital.example.org/mrn";
    /** Unique per run: keeps the assertions independent of other tests and runs. */
    private static final String IDENTIFIER_VALUE = "MRN-IT-" + UUID.randomUUID();

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private FhirContext fhirContext;

    @Test
    void registeringTheSameIdentifierTwiceKeepsExactlyOneFhirPatient() {
        PatientRequest request = new PatientRequest(
                IDENTIFIER_SYSTEM, IDENTIFIER_VALUE, "Workflow", "Wendy", LocalDate.of(1985, 2, 20), "female");

        ResponseEntity<PatientResponse> firstRegistration =
                restTemplate.postForEntity("/api/v1/patients", request, PatientResponse.class);
        assertThat(firstRegistration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PatientResponse registered = firstRegistration.getBody();
        assertThat(registered).isNotNull();
        assertThat(registered.resourceId()).isNotBlank();
        assertThat(registered.identifierSystem()).isEqualTo(IDENTIFIER_SYSTEM);
        assertThat(registered.identifierValue()).isEqualTo(IDENTIFIER_VALUE);
        assertThat(firstRegistration.getHeaders().getLocation())
                .hasToString("/api/v1/patients/" + registered.resourceId());

        ResponseEntity<PatientResponse> retrieval =
                restTemplate.getForEntity("/api/v1/patients/{id}", PatientResponse.class, registered.resourceId());
        assertThat(retrieval.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retrieval.getBody()).isEqualTo(registered);

        Bundle firstSearch = searchFhirPatientsByIdentifier();
        assertThat(firstSearch.getTotal()).isEqualTo(1);
        assertThat(firstSearch.getEntry()).hasSize(1);
        Patient storedOnce = (Patient) firstSearch.getEntryFirstRep().getResource();
        assertThat(storedOnce.getIdentifierFirstRep().getSystem()).isEqualTo(IDENTIFIER_SYSTEM);
        assertThat(storedOnce.getIdentifierFirstRep().getValue()).isEqualTo(IDENTIFIER_VALUE);

        ResponseEntity<PatientResponse> secondRegistration =
                restTemplate.postForEntity("/api/v1/patients", request, PatientResponse.class);
        assertThat(secondRegistration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PatientResponse reRegistered = secondRegistration.getBody();
        assertThat(reRegistered).isNotNull();
        assertThat(reRegistered.resourceId()).isEqualTo(registered.resourceId());

        Bundle secondSearch = searchFhirPatientsByIdentifier();
        assertThat(secondSearch.getTotal()).isEqualTo(1);
        assertThat(secondSearch.getEntry()).hasSize(1);
    }

    /**
     * Issues {@code GET /fhir/Patient?identifier=<system>|<value>} directly at
     * the FHIR server, the milestone M2 check, and parses the response as a
     * FHIR search Bundle.
     */
    private Bundle searchFhirPatientsByIdentifier() {
        String encodedIdentifier =
                URLEncoder.encode(IDENTIFIER_SYSTEM + "|" + IDENTIFIER_VALUE, StandardCharsets.UTF_8);
        ResponseEntity<String> response = restTemplate.getForEntity(
                URI.create(fhirBaseUrl() + "/Patient?identifier=" + encodedIdentifier), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return (Bundle) fhirContext.newJsonParser().parseResource(response.getBody());
    }
}

package com.example.healthcare.patient;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc tests for the patient REST endpoints and the ProblemDetail error
 * contract from {@code documentation/architecture/api-conventions.md}.
 */
@WebMvcTest(PatientController.class)
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientService patientService;

    @Test
    void returns201WithALocationHeaderAndTheRegisteredPatient() throws Exception {
        when(patientService.register(new PatientRequest(
                "http://hospital.example.org/mrn", "MRN-1001", "Doe", "John",
                java.time.LocalDate.of(1990, 5, 15), "male")))
                .thenReturn(new PatientResponse("pat-123", "http://hospital.example.org/mrn", "MRN-1001"));

        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifierSystem": "http://hospital.example.org/mrn",
                                  "identifierValue": "MRN-1001",
                                  "familyName": "Doe",
                                  "givenName": "John",
                                  "birthDate": "1990-05-15",
                                  "gender": "male"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/patients/pat-123"))
                .andExpect(jsonPath("$.resourceId").value("pat-123"))
                .andExpect(jsonPath("$.identifierSystem").value("http://hospital.example.org/mrn"))
                .andExpect(jsonPath("$.identifierValue").value("MRN-1001"));
    }

    @Test
    void returns400WithFieldErrorsWhenValidationFails() throws Exception {
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifierSystem": "http://hospital.example.org/mrn",
                                  "identifierValue": " ",
                                  "familyName": "",
                                  "givenName": "John",
                                  "birthDate": "2990-05-15",
                                  "gender": "male"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed for one or more fields"))
                .andExpect(jsonPath("$.instance").value("/api/v1/patients"))
                .andExpect(jsonPath("$.errors.identifierValue").value("must not be blank"))
                .andExpect(jsonPath("$.errors.familyName").value("must not be blank"))
                .andExpect(jsonPath("$.errors.birthDate").value("must be a past or present date"));

        verifyNoInteractions(patientService);
    }

    @Test
    void returns400WithTheProblemDetailShapeForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/v1/patients"));

        verifyNoInteractions(patientService);
    }

    @Test
    void returns200WithThePatientWhenItExists() throws Exception {
        when(patientService.findById("pat-123"))
                .thenReturn(new PatientResponse("pat-123", "http://hospital.example.org/mrn", "MRN-1001"));

        mockMvc.perform(get("/api/v1/patients/pat-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value("pat-123"))
                .andExpect(jsonPath("$.identifierSystem").value("http://hospital.example.org/mrn"))
                .andExpect(jsonPath("$.identifierValue").value("MRN-1001"));
    }

    @Test
    void returns404WithAProblemDetailWhenThePatientIsMissing() throws Exception {
        when(patientService.findById("pat-404"))
                .thenThrow(new PatientService.PatientNotFoundException("No patient found with resource id pat-404"));

        mockMvc.perform(get("/api/v1/patients/pat-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No patient found with resource id pat-404"))
                .andExpect(jsonPath("$.instance").value("/api/v1/patients/pat-404"));
    }
}

package com.example.healthcare.patient;

import java.net.URI;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for patient registration and retrieval under {@code /api/v1}.
 *
 * <p>Holds no business logic: it validates and translates HTTP concerns
 * (status codes, the {@code Location} header) and delegates to
 * {@link PatientService}. FHIR types never appear here.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @PostMapping
    public ResponseEntity<PatientResponse> register(@Valid @RequestBody PatientRequest request) {
        PatientResponse response = patientService.register(request);
        return ResponseEntity.created(URI.create("/api/v1/patients/" + response.resourceId())).body(response);
    }

    @GetMapping("/{id}")
    public PatientResponse findById(@PathVariable String id) {
        return patientService.findById(id);
    }
}

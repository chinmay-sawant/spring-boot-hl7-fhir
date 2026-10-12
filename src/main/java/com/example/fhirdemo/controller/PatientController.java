package com.example.fhirdemo.controller;

import com.example.fhirdemo.model.PatientEntity;
import com.example.fhirdemo.service.FhirQueryService;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Patient search + raw FHIR JSON view. Reads from H2 via FhirQueryService.
 *
 * <p>Assumed service shapes (created in parallel):
 * <ul>
 *   <li>List&lt;PatientEntity&gt; searchPatientsByFamily(String familyOrNull)</li>
 *   <li>String getPatientFhirJson(String id)</li>
 * </ul>
 */
@RestController
@RequestMapping(value = "/api/patients", produces = MediaType.APPLICATION_JSON_VALUE)
public class PatientController {

  private final FhirQueryService fhirQueryService;

  public PatientController(FhirQueryService fhirQueryService) {
    this.fhirQueryService = fhirQueryService;
  }

  @GetMapping
  public ResponseEntity<?> search(@RequestParam(value = "name", required = false) String name) {
    try {
      List<PatientEntity> result = fhirQueryService.searchPatientsByFamily(name);
      return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> getById(@PathVariable String id) {
    try {
      String json = fhirQueryService.getPatientFhirJson(id);
      if (json == null) {
        return ResponseEntity.status(404).body(Map.of("error", "Patient not found: " + id));
      }
      return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
  }
}

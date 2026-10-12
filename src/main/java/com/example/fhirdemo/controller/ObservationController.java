package com.example.fhirdemo.controller;

import com.example.fhirdemo.model.ObservationEntity;
import com.example.fhirdemo.repository.ObservationRepository;
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
 * Observation search + raw FHIR JSON view.
 *
 * <p>Assumed shapes:
 * <ul>
 *   <li>FhirQueryService.listObservationsForPatient(String patientRefOrNull)</li>
 *   <li>ObservationEntity.getFhirId() / getResourceJson()</li>
 *   <li>ObservationRepository extends JpaRepository (findAll only; filtered in memory
 *       to avoid depending on custom finder names from parallel workstream)</li>
 * </ul>
 */
@RestController
@RequestMapping(value = "/api/observations", produces = MediaType.APPLICATION_JSON_VALUE)
public class ObservationController {

  private final FhirQueryService fhirQueryService;
  private final ObservationRepository observationRepository;

  public ObservationController(
      FhirQueryService fhirQueryService, ObservationRepository observationRepository) {
    this.fhirQueryService = fhirQueryService;
    this.observationRepository = observationRepository;
  }

  @GetMapping
  public ResponseEntity<?> search(
      @RequestParam(value = "patient", required = false) String patient) {
    try {
      List<ObservationEntity> result = fhirQueryService.listObservationsForPatient(patient);
      return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> getById(@PathVariable String id) {
    try {
      return observationRepository.findAll().stream()
          .filter(e -> id.equals(e.getFhirId()))
          .findFirst()
          .<ResponseEntity<?>>map(
              e ->
                  ResponseEntity.ok()
                      .contentType(MediaType.APPLICATION_JSON)
                      .body(e.getResourceJson()))
          .orElseGet(
              () ->
                  ResponseEntity.status(404)
                      .body(Map.of("error", "Observation not found: " + id)));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
  }
}

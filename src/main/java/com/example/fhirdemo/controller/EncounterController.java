package com.example.fhirdemo.controller;

import com.example.fhirdemo.model.EncounterEntity;
import com.example.fhirdemo.repository.EncounterRepository;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Encounter list + raw FHIR JSON view. Reads directly from H2 repository
 * (no dedicated query service for encounters by design).
 *
 * <p>Only depends on EncounterRepository.findAll() and
 * EncounterEntity.getFhirId()/getResourceJson() to stay compile-safe
 * against the parallel entities workstream.
 */
@RestController
@RequestMapping(value = "/api/encounters", produces = MediaType.APPLICATION_JSON_VALUE)
public class EncounterController {

  private final EncounterRepository encounterRepository;

  public EncounterController(EncounterRepository encounterRepository) {
    this.encounterRepository = encounterRepository;
  }

  @GetMapping
  public ResponseEntity<?> list() {
    try {
      List<EncounterEntity> result = encounterRepository.findAll();
      return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> getById(@PathVariable String id) {
    try {
      return encounterRepository.findAll().stream()
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
                      .body(Map.of("error", "Encounter not found: " + id)));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
  }
}

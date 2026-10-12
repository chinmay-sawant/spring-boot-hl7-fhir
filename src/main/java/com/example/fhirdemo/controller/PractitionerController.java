package com.example.fhirdemo.controller;

import com.example.fhirdemo.model.PractitionerEntity;
import com.example.fhirdemo.repository.PractitionerRepository;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Practitioner list. Reads directly from H2 repository. */
@RestController
@RequestMapping(value = "/api/practitioners", produces = MediaType.APPLICATION_JSON_VALUE)
public class PractitionerController {

  private final PractitionerRepository practitionerRepository;

  public PractitionerController(PractitionerRepository practitionerRepository) {
    this.practitionerRepository = practitionerRepository;
  }

  @GetMapping
  public ResponseEntity<?> list() {
    try {
      List<PractitionerEntity> result = practitionerRepository.findAll();
      return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
  }
}

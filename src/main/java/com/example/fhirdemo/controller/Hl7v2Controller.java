package com.example.fhirdemo.controller;

import ca.uhn.fhir.context.FhirContext;
import com.example.fhirdemo.service.Hl7v2Service;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HL7v2 demo endpoints: parse sample, parse posted message, convert sample ADT to FHIR Patient,
 * generate ADT from given demographics.
 */
@RestController
@RequestMapping(value = "/api/hl7v2", produces = MediaType.APPLICATION_JSON_VALUE)
public class Hl7v2Controller {

  private final Hl7v2Service hl7v2Service;
  private final FhirContext fhirContext;

  public Hl7v2Controller(Hl7v2Service hl7v2Service, FhirContext fhirContext) {
    this.hl7v2Service = hl7v2Service;
    this.fhirContext = fhirContext;
  }

  @GetMapping("/parse")
  public ResponseEntity<?> parseSample() {
    try {
      return ResponseEntity.ok().body(hl7v2Service.parseSampleFile());
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
  }

  @PostMapping(
      value = "/parse",
      consumes = {MediaType.TEXT_PLAIN_VALUE, "application/hl7-v2", MediaType.ALL_VALUE})
  public ResponseEntity<?> parseBody(@RequestBody(required = false) String hl7) {
    if (hl7 == null || hl7.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Request body must contain an HL7v2 message"));
    }
    try {
      return ResponseEntity.ok().body(hl7v2Service.parseAdt(hl7));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
  }

  @GetMapping("/to-fhir")
  public ResponseEntity<?> toFhir() {
    try (var in = getClass().getResourceAsStream("/hl7-samples/adt-a01.hl7")) {
      if (in == null) {
        return ResponseEntity.internalServerError().body(Map.of("error", "Sample HL7 file not found"));
      }
      String hl7 = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
      Patient patient = hl7v2Service.convertAdtToFhirPatient(hl7);
      String fhirJson = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(patient);
      return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(fhirJson);
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
  }

  @PostMapping("/generate")
  public ResponseEntity<?> generate(
      @RequestParam(value = "family", defaultValue = "Smith") String family,
      @RequestParam(value = "given", defaultValue = "John") String given,
      @RequestParam(value = "dob", defaultValue = "19800515") String dob,
      @RequestParam(value = "gender", defaultValue = "M") String gender,
      @RequestParam(value = "id", defaultValue = "12345") String id) {
    try {
      String hl7 = hl7v2Service.generateAdtForPatient(family, given, dob, gender, id);
      return ResponseEntity.ok().body(Map.of("hl7", hl7));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
  }
}

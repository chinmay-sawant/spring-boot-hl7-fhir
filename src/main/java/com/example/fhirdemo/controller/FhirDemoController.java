package com.example.fhirdemo.controller;

import com.example.fhirdemo.service.FhirValidationService;
import com.example.fhirdemo.service.HapiFhirFetchService;
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
 * FHIR demo endpoints: validation + live HAPI fetch.
 *
 * <p>Assumed shapes:
 * <ul>
 *   <li>FhirValidationService.validateResource(String json) returns List&lt;String&gt; issues
 *       (empty = valid)</li>
 *   <li>HapiFhirFetchService.fetchPatient(String id) returns String raw FHIR JSON</li>
 * </ul>
 */
@RestController
@RequestMapping(value = "/api/demo", produces = MediaType.APPLICATION_JSON_VALUE)
public class FhirDemoController {

  private final FhirValidationService validationService;
  private final HapiFhirFetchService fetchService;

  public FhirDemoController(
      FhirValidationService validationService, HapiFhirFetchService fetchService) {
    this.validationService = validationService;
    this.fetchService = fetchService;
  }

  @GetMapping("/validate")
  public ResponseEntity<?> validate(@RequestParam(value = "resource", required = false) String resource) {
    if (resource == null || resource.isBlank()) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "Missing required query param: resource (URL-encoded FHIR JSON)"));
    }
    try {
      List<String> issues = validationService.validateResource(resource);
      boolean valid = issues == null || issues.isEmpty();
      return ResponseEntity.ok()
          .body(Map.of("valid", valid, "issues", issues == null ? List.of() : issues));
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
  }

  @GetMapping("/live-patient/{id}")
  public ResponseEntity<?> livePatient(@PathVariable String id) {
    try {
      String json = fetchService.fetchPatient(id);
      if (json == null) {
        return ResponseEntity.status(502).body(Map.of("error", "Live fetch returned empty for id: " + id));
      }
      return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    } catch (Exception e) {
      // Offline / upstream failure -> 502, not 500, to distinguish from local bugs.
      return ResponseEntity.status(502).body(Map.of("error", "Live fetch failed: " + e.getMessage()));
    }
  }
}

# Spring Boot Healthcare Platform - 03 Clinical Data and Laboratory

> **Parent:** `plans/demo/init.md` - source architecture brief
> **Status:** not started
> **Estimated effort:** 2-3 weeks for one developer

---

## Overview

This plan delivers the clinical data and laboratory half of the platform: LOINC-coded vital-sign Observations, ORU R01 to FHIR mapping, laboratory orders and results workflows, and AllergyIntolerance, Condition, and MedicationRequest records. It follows plan 01 (foundation) and plan 02 (HL7 admissions), because observation and laboratory linkage needs existing Patient and Encounter resources. It hands plan 04 stable clinical services for the simulator, retry, audit, and metrics work, and it keeps the HAPI FHIR R4 server as the source of truth with no duplicate clinical database.

## Executive Summary

- M6 from init.md section 11 is demonstrable: vital signs and laboratory results are recorded as FHIR R4 Observations in the HAPI FHIR server and reference the correct Patient and Encounter resources.
- ORU R01 maps OBR to ServiceRequest and DiagnosticReport and OBX to Observation, including result status, UCUM units, and reference ranges, with `ObservationHl7Mapper` and `DiagnosticReportHl7Mapper` unit tests over a deterministic sample message; the handler registers with the plan 02 router so ORU R01 flows through the message pipeline end to end.
- `POST /api/v1/laboratory/orders` and `POST /api/v1/laboratory/results` run the order and result workflows end to end through the FHIR client.
- `POST /api/v1/clinical/allergies`, `POST /api/v1/clinical/conditions`, and `POST /api/v1/clinical/medications` create AllergyIntolerance, Condition, and MedicationRequest resources with Bean Validation and consistent errors.
- Laboratory linkage is verified by a positive test (results reference the intended patient and encounter) and a negative test (a mismatched patient identifier is rejected with no persisted resources).
- The exact mutually compatible versions of Spring Boot 3.x, HAPI HL7v2, and HAPI FHIR R4 are verified and recorded under JDK 17 before coding starts.

## Phase 1: Observation foundation

### 1.1 Dependency and fixture baseline

- [ ] Confirm in the root `pom.xml` and `healthcare-fhir/pom.xml` that the inherited Spring Boot 3.x, HAPI HL7v2, and HAPI FHIR R4 versions are the latest mutually compatible releases on JDK 17; check Maven Central and HAPI release notes and write the resolved versions with their sources into this plan. Proof: `./mvnw -q dependency:tree` output filtered for `spring-boot`, `hapi-fhir`, and `hapi-hl7v2`; never record a version that was not verified.
- [ ] Confirm `healthcare-fhir` resolves the HAPI FHIR R4 structures used later (`Observation`, `DiagnosticReport`, `ServiceRequest`) and add the HAPI HL7v2 dependency needed by the phase 2 mappers if plan 01 did not already provide it. Proof: `./mvnw -pl healthcare-fhir compile` plus the dependency tree from the previous row.
- [ ] Add deterministic fixtures `samples/fhir/observation.json` and `samples/fhir/diagnostic-report.json` with synthetic values and fixed identifiers, matching the repository layout in init.md section 2. Proof: a fixture loader test parses both files into HAPI FHIR R4 resources.

### 1.2 Terminology and unit helpers

- [ ] Add `healthcare-fhir/src/main/java/com/example/healthcare/fhir/terminology/LoincCodingHelper.java` to build `Coding` and `CodeableConcept` values with system `http://loinc.org`; reject blank codes. Proof: `LoincCodingHelperTest` asserts system, code, and display.
- [ ] Add `healthcare-fhir/src/main/java/com/example/healthcare/fhir/terminology/UcumUnitHelper.java` to build `Quantity` values with system `http://unitsofmeasure.org` from a supplied unit code, and leave the unit unset when no code is supplied. Proof: unit tests for one metric unit, one US customary unit, and one missing unit case.

### 1.3 Observation creation and reference resolution

- [ ] Add `healthcare-fhir/src/main/java/com/example/healthcare/fhir/mapper/ObservationMapper.java` to assemble a FHIR R4 `Observation` from a LOINC code, a value with UCUM unit, an effective time, a status, and Patient and Encounter references; vital signs use it first and laboratory results reuse it in phase 3. Proof: `ObservationMapperTest` with deterministic fixtures (fixed clock and identifiers) asserts every field and stable serialization across repeated runs.
- [ ] Add `healthcare-fhir/src/main/java/com/example/healthcare/fhir/mapper/ClinicalReferenceResolver.java` to resolve a Patient by identifier system and value and an Encounter by visit identifier through the FHIR client port; it returns typed `Reference` objects, throws a typed exception on no match or an ambiguous match, and never treats a business identifier as a resource ID. Proof: unit tests with a mocked client for single match, no match, and multiple matches.
- [ ] Add typed domain failures under `healthcare-domain/src/main/java/com/example/healthcare/domain/exception/` for reference resolution and validation failures so `healthcare-fhir` and `healthcare-application` share one error vocabulary that phase 5 maps to HTTP statuses. Proof: `ClinicalReferenceResolverTest` and `ObservationServiceTest` throw and assert the typed exceptions.
- [ ] Add `healthcare-application/src/main/java/com/example/healthcare/laboratory/ObservationService.java` to resolve references, call the mapper, run validation, and persist through the FHIR client port; the service owns the workflow decision and the mapper stays free of business rules. Proof: `ObservationServiceTest` with a mocked port captures the persisted Observation and its references.

### 1.4 FHIR validation

- [ ] Route every Observation created by the service through the HAPI validation support in `healthcare-fhir/src/main/java/com/example/healthcare/fhir/validator/`; if plan 01 exposes validation only for its own resources, add the minimal resource-level entry point in this row and note the extension here, and stop persistence when validation fails. Proof: a valid vital-sign Observation passes and an Observation missing `code` fails with recorded issues.

### 1.5 Closure gate

- [ ] Run `./mvnw -pl healthcare-fhir,healthcare-application test` with the phase 1 selectors (`*CodingHelperTest`, `*UnitHelperTest`, `ObservationMapperTest`, `ClinicalReferenceResolverTest`, `ObservationServiceTest`, observation validation test) and record pass and fail counts in this plan; leave this row unchecked if any test fails.
- [ ] Confirm the versions recorded under 1.1 still match the classpath after the module changes and note any drift in this plan.

## Phase 2: ORU R01 parsing and mapping

### 2.1 Sample ORU R01

- [ ] Add `samples/hl7/oru-r01.hl7` as a deterministic HL7 v2.5.1 ORU^R01 message with MSH, PID, PV1 for the visit, one OBR, and at least two OBX results carrying LOINC codes, UCUM units, reference ranges, and different result statuses; all identifiers are synthetic and the file header records the assumptions. Proof: a test parses the file with the HAPI pipe parser and asserts MSH-9 is `ORU^R01`, one OBR group, and two OBX segments.

### 2.2 OBX to Observation

- [ ] Implement `healthcare-fhir/src/main/java/com/example/healthcare/fhir/mapper/ObservationHl7Mapper.java` with the parsed HAPI `ORU_R01` as input: OBX-3 to `Observation.code` through `LoincCodingHelper`, OBX-2 and OBX-5 numeric values to `valueQuantity` with the OBX-6 unit through `UcumUnitHelper`, string values to `valueString`, and OBX-7 to `referenceRange`. Proof: `ObservationHl7MapperTest` over the sample message asserts code, value, unit, and range for both OBX segments.
- [ ] Map OBX-11 result status for preliminary (P), final (F), corrected (C), and cancelled (X) in the same mapper, with a documented and tested policy for unsupported codes (default: fail the mapping and record the code); map effective time from OBX-14 with the OBR-7 fallback and never from MSH-7. Proof: parameterized status test and an effective time test with OBX-14 present and absent.
- [ ] Set `Observation.subject` from the PID-3 patient identifier and `Observation.encounter` from the visit identifier in the same mapper, resolving both through `ClinicalReferenceResolver`; a reference that does not resolve fails the mapping instead of producing an unlinked Observation. Proof: resolution success and failure tests.

### 2.3 OBR to ServiceRequest and DiagnosticReport

- [ ] Implement `healthcare-fhir/src/main/java/com/example/healthcare/fhir/mapper/DiagnosticReportHl7Mapper.java`: OBR-4 to `ServiceRequest.code` and `DiagnosticReport.code`, OBR-2 and OBR-3 order numbers to `identifier` values, OBR-7 to `effectiveDateTime`, and subject and encounter from the resolved references. Proof: `DiagnosticReportHl7MapperTest` over the sample message asserts the ServiceRequest, report header fields, and references.
- [ ] Assemble the result linkage in the same mapper: `DiagnosticReport.result` references each Observation from the OBX group, and every linked Observation carries the same subject and encounter as the report. Proof: test asserts one report with two result references and matching references on each Observation.
- [ ] Derive `DiagnosticReport.status` from OBR-25 with a documented fallback to the contained OBX statuses. Proof: tests for a final report and a preliminary report.

### 2.4 ORU R01 handler and router registration

- [ ] Register an ORU R01 handler with the plan 02 `Hl7MessageRouter` from `healthcare-application` so `ORU^R01` no longer resolves to the unsupported handler: map the message through `ObservationHl7Mapper` and `DiagnosticReportHl7Mapper`, persist the group through the FHIR transaction client, and reuse the plan 02 duplicate detection and processing status. Proof: unit test routes `ORU^R01` to the new handler and an integration test submits `samples/hl7/oru-r01.hl7` through `POST /api/v1/messages/hl7` and asserts `AA` plus persisted resources.
- [ ] Route ORU R01 failure outcomes through the plan 02 error recording: mapping or validation failures record `error_code` and `error_summary` on the inbound row and return a definite non-success ACK with no persisted resources. Proof: test with an unresolvable patient reference asserts the error row and the non-success ACK.

### 2.5 Mapping documentation

- [ ] Document the ORU R01 mapping in `documentation/hl7/message-types.md`: required segments, the OBR and OBX to FHIR field table, status mapping, unit handling, effective time fallback, and linkage rules, following init.md section 5 and section 16. Proof: the section exists and matches the tests in 2.2 and 2.3.

### 2.6 Closure gate

- [ ] Run `./mvnw -pl healthcare-fhir test -Dtest='ObservationHl7MapperTest,DiagnosticReportHl7MapperTest'` (adjust selectors to the final class names) and record pass and fail counts in this plan; leave unchecked on failure.
- [ ] Replay `samples/hl7/oru-r01.hl7` through the mapper tests and record the mapped ServiceRequest, DiagnosticReport, and Observation references in this plan as the phase evidence.

## Phase 3: Laboratory orders and results workflows

### 3.1 Order workflow

- [ ] Add `healthcare-application/src/main/java/com/example/healthcare/laboratory/LaboratoryOrderRequest.java` with patient identifier, encounter visit identifier, a LOINC order code, and a requested time, annotated with Bean Validation constraints per init.md section 11. Proof: DTO validation tests for blank and malformed fields.
- [ ] Add `placeOrder` in `healthcare-application/src/main/java/com/example/healthcare/laboratory/LaboratoryService.java`: resolve Patient and Encounter through `ClinicalReferenceResolver`, build the ServiceRequest order with LOINC coding and identifiers, run FHIR validation, and persist through the FHIR client port. Proof: service test with a mocked port and a resolver not-found case.
- [ ] Add `healthcare-application/src/main/java/com/example/healthcare/laboratory/LaboratoryController.java` exposing `POST /api/v1/laboratory/orders` and returning HTTP 201 with the created order identifier; the controller only delegates. Proof: MockMvc tests for success and validation failure.

### 3.2 Result workflow and DiagnosticReport generation

- [ ] Add `LaboratoryResultRequest` with the order identifier, patient identifier, encounter visit identifier, and one or more result items carrying LOINC code, value, unit, reference range, and status, with Bean Validation. Proof: DTO validation tests.
- [ ] Add `recordResult` in `LaboratoryService`: create Observations through `ObservationMapper`, generate a DiagnosticReport with result references to every Observation, link the report to the same patient and encounter as the order, and persist the group as a FHIR transaction Bundle where the server supports it per init.md section 4. Proof: service test asserts one DiagnosticReport with result references to each Observation and matching subject and encounter references.
- [ ] Add `POST /api/v1/laboratory/results` to `LaboratoryController`, returning HTTP 201 with the DiagnosticReport identifier. Proof: MockMvc tests for success and an unknown order identifier.

### 3.3 M6 evidence

- [ ] Add `healthcare-integration-tests/src/test/java/com/example/healthcare/LaboratoryWorkflowIT.java` that creates a known Patient and Encounter, places an order, records a result, then reads back the DiagnosticReport and Observations from the HAPI FHIR server and asserts the references; reuse the integration test infrastructure from plan 01 (Testcontainers or the running docker compose stack). Proof: test output recorded in this plan.
- [ ] Record M6 vital-sign evidence: run `ObservationService` against the local HAPI FHIR server from a test or small demo runner, fetch the result with `GET /fhir/Observation?patient=...`, and paste the response with its LOINC code and UCUM unit into this plan.
- [ ] Record M6 laboratory evidence: run the curl sequence `POST /api/v1/laboratory/orders`, `POST /api/v1/laboratory/results`, `GET /fhir/DiagnosticReport?patient=...` against the local stack and paste status codes and returned resource identifiers into this plan; leave the row unchecked if any step fails.

### 3.4 Closure gate

- [ ] Run `./mvnw -pl healthcare-application test` plus the `healthcare-integration-tests` run of `LaboratoryWorkflowIT` and record the totals in this plan; leave unchecked if any test fails.
- [ ] Confirm with `GET /fhir/Observation?patient=...` that observed vital signs and laboratory results resolve to the intended patient, and record the query output in this plan.

## Phase 4: Allergies, diagnoses, and medications

### 4.1 Clinical terminology helpers

- [ ] Extend `healthcare-fhir/src/main/java/com/example/healthcare/fhir/terminology/` with SNOMED CT (`http://snomed.info/sct`) and RxNorm (`http://www.nlm.nih.gov/research/umls/rxnorm`) coding helpers mirroring `LoincCodingHelper`; blank codes are rejected. Proof: unit tests assert system, code, and display for both.
- [ ] Append clinical terminology usage to `documentation/fhir/terminology.md`: LOINC for observations, SNOMED CT for AllergyIntolerance and Condition codes, RxNorm for MedicationRequest medication coding, with a cross-reference to the ORU mapping doc. Proof: section exists.

### 4.2 AllergyIntolerance

- [ ] Add `healthcare-application/src/main/java/com/example/healthcare/clinical/AllergyIntoleranceRequest.java` with patient identifier, a SNOMED CT code, category, criticality, and clinical status, with Bean Validation. Proof: DTO validation tests.
- [ ] Add `healthcare-application/src/main/java/com/example/healthcare/clinical/AllergyIntoleranceService.java` to resolve the Patient reference, map the DTO to a FHIR R4 `AllergyIntolerance`, run FHIR validation, and persist through the FHIR client port. Proof: service test with a mocked port asserts the resource and its subject reference.
- [ ] Add `healthcare-application/src/main/java/com/example/healthcare/clinical/AllergyIntoleranceController.java` with `POST /api/v1/clinical/allergies` and `GET /api/v1/clinical/allergies/{id}`; unknown patients return the standard error body. Proof: MockMvc tests for both endpoints.

### 4.3 Condition

- [ ] Add `healthcare-application/src/main/java/com/example/healthcare/clinical/ConditionRequest.java` with patient identifier, a SNOMED CT code, clinical status, and recorded date, with Bean Validation. Proof: DTO validation tests.
- [ ] Add `healthcare-application/src/main/java/com/example/healthcare/clinical/ConditionService.java` to map the DTO to a FHIR R4 `Condition` with a resolved subject reference, run FHIR validation, and persist. Proof: service test with a mocked port.
- [ ] Add `healthcare-application/src/main/java/com/example/healthcare/clinical/ConditionController.java` with `POST /api/v1/clinical/conditions` and `GET /api/v1/clinical/conditions/{id}`. Proof: MockMvc tests for both endpoints.

### 4.4 MedicationRequest

- [ ] Add `healthcare-application/src/main/java/com/example/healthcare/clinical/MedicationOrderRequest.java` with patient identifier, an RxNorm medication coding, dosage instruction text, and authoredOn, with Bean Validation; it maps to a FHIR R4 `MedicationRequest`. Proof: DTO validation tests.
- [ ] Add `healthcare-application/src/main/java/com/example/healthcare/clinical/MedicationRequestService.java` to build the `MedicationRequest` with status active and intent order, resolve the subject reference, run FHIR validation, and persist. Proof: service test with a mocked port asserts the resource and required FHIR fields.
- [ ] Add `healthcare-application/src/main/java/com/example/healthcare/clinical/MedicationRequestController.java` with `POST /api/v1/clinical/medications` and `GET /api/v1/clinical/medications/{id}`. Proof: MockMvc tests for both endpoints.

### 4.5 Closure gate

- [ ] Run `./mvnw -pl healthcare-fhir,healthcare-application test` and record pass and fail counts in this plan; leave unchecked on failure.
- [ ] curl the three clinical POST endpoints against the local stack and record response codes and resource identifiers in this plan.

## Phase 5: Error handling, linkage verification, and plan closure

### 5.1 Consistent error handling

- [ ] Extend the global exception handling from plan 01 with mappings for clinical errors: reference not found to HTTP 404, ambiguous reference to HTTP 409, FHIR validation failure to HTTP 422, and unsupported terminology code to HTTP 400; document the status mapping in this plan. Proof: MockMvc tests per exception type.
- [ ] Ensure Bean Validation failures on the laboratory and clinical request DTOs return HTTP 400 with field-level details through the same handler. Proof: controller tests for one laboratory DTO and one clinical DTO.
- [ ] Verify error bodies contain no stack traces or raw clinical payloads, following the sensitive data guidance in init.md section 5. Proof: response body shape test plus a short note in this plan.

### 5.2 Laboratory linkage verification

- [ ] Add a two-patient linkage test in `healthcare-integration-tests` that records a laboratory result for Patient A and asserts the DiagnosticReport and every Observation subject reference resolves to Patient A and the encounter belongs to Patient A; this is test scenario 7 from init.md section 15. Proof: recorded test output.
- [ ] Add a negative linkage test where the result payload names a patient different from the order's patient and assert HTTP 409, the standard error body, and no persisted resources. Proof: test output plus an empty FHIR search result recorded in this plan.

### 5.3 Plan closure gate

- [ ] Run `./mvnw test` across `healthcare-domain`, `healthcare-fhir`, `healthcare-hl7`, `healthcare-application`, and `healthcare-integration-tests` and record the totals in this plan; leave unchecked if any module fails.
- [ ] Re-run the ORU R01 mapper replay and the M6 curl sequence from 3.3 and paste the outcomes into this plan as the final M6 evidence.
- [ ] Audit every row: mark `[x]` only where the proof ran successfully, mark `[~]` with a reason and next gate where deferred, and leave failed rows unchecked. Proof: the updated row statuses in this file.

## Dependencies

- Requires from `plans/spring-boot/01-foundation.md`: the Maven multi-module skeleton with JDK 17 and the latest stable Spring Boot 3.x, the `healthcare-fhir` FHIR client port and HAPI FHIR client configuration, the local HAPI FHIR R4 server in docker compose, the global exception handler baseline, FHIR validation support, and `samples/` conventions. This plan must not re-create client or server configuration.
- Requires from `plans/spring-boot/02-hl7-admissions.md`: Patient registration and Encounter creation from ADT A01 and the REST endpoints, because observation and laboratory linkage needs existing resources; once the receiver and router exist, this plan registers the ORU R01 handler so ORU messages dispatch to the mappers defined here. Mapper correctness and end-to-end ORU ingestion through the receiver are both validated in this plan.
- Hands to `plans/spring-boot/04-reliability-ops.md`: `ObservationService`, `LaboratoryService`, and the clinical services for the section 10 synthetic data seeder (vital-sign observations, laboratory reports, allergies, diagnoses, medications), plus the ORU mappers for retry, audit, and metrics wrapping. Plan 04 owns the simulator, message state machine, security, observability, and dashboard.
- Canonical ledger note: all active rows for clinical data and laboratory scope live in this file; work owned by a sibling plan lives in that sibling file and is only referenced here.

# Spring Boot Healthcare Platform - 02 HL7 v2 Pipeline and ADT Workflows

> **Parent:** `plans/demo/init.md` - source architecture brief
> **Status:** not started
> **Estimated effort:** 3-4 weeks for one developer

---

## Overview

This plan delivers the HL7 v2 pipeline and the ADT A04 registration, ADT A08 update, ADT A01 admission, ADT A02 transfer, and ADT A03 discharge workflows of the Healthcare Interoperability Learning Platform. It builds parsing and validation in healthcare-hl7, message routing with original and enhanced acknowledgment modes, inbound message persistence, an MLLP receiver, and REST endpoints for message submission, processing status, encounters, and the patient timeline. It assumes plan 1 (foundation) is complete and hands retry, quarantine, dashboard, simulator, security, and observability work to plan 4. The tech baseline for this plan is JDK 17 and the latest stable Spring Boot 3.x release, per user direction, overriding the Java 21 value in init.md.

## Executive Summary

- M3: submitting an ADT A04 message over MLLP or `POST /api/v1/messages/hl7` creates a FHIR R4 Patient through `PatientHl7Mapper` with PID-3 identifier, PID-5 name, PID-7 birth date, and PID-8 gender, and persists a `resource_mappings` row; an ADT A08 message for the same identifier updates the existing Patient instead of creating a second one.
- M4: submitting an ADT A01 message creates Patient plus Encounter in one FHIR transaction Bundle, with PV1-2 mapped to `Encounter.class` and the hospital visit identifier preserved so retransmission cannot create duplicate encounters.
- M5: submitting ADT A02 and ADT A03 for the same patient adds location history on transfer and sets Encounter status finished on discharge, demonstrable through messages, the encounter REST endpoints, and the patient timeline.
- Supporting state: healthcare-hl7 holds parser, validator, router, and ACK generator; inbound_messages tracks MSH-10 and processing status; duplicate messages return the prior result without repeating successful work.
- Not in this plan: ORU R01 and clinical resources (plan 3); retry and quarantine depth, dashboard, simulator, security, and observability (plan 4).

## Phase 1: HL7 parsing and validation

### 1.1 Dependency baseline

- [ ] At implementation time, verify the exact mutually compatible versions of the latest stable Spring Boot 3.x, HAPI HL7v2, and HAPI FHIR R4 on JDK 17 against official release notes and documentation; do not assume any version from this plan. Record the verified versions with source links under this phase's closure gate and confirm they match the version properties in the plan 1 root `pom.xml`; update the properties as part of this baseline if they differ. Proof: the recorded version note plus `./mvnw -q dependency:tree` output resolving the HAPI HL7v2 and HAPI FHIR artifacts at the recorded versions.
- [ ] Add the HAPI HL7v2 dependency to `healthcare-hl7/pom.xml` and confirm the module compiles and tests on JDK 17 with the parent build settings from plan 1. Proof: `./mvnw -pl healthcare-hl7 -am compile` succeeds and the resolved HL7v2 version matches the recorded baseline.

### 1.2 Sample messages

- [ ] Add `samples/hl7/adt-a04.hl7`, `samples/hl7/adt-a08.hl7`, `samples/hl7/adt-a01.hl7`, `samples/hl7/adt-a02.hl7`, and `samples/hl7/adt-a03.hl7` in HL7 v2.5.1 pipe format with distinct MSH-10 values, synthetic PID data, PV1 data where the type requires it, and one shared hospital visit identifier across the admission, transfer, and discharge samples. Proof: the parser and validator tests consume these files and pass.

### 1.3 Parser and typed message model

- [ ] Implement `Hl7ParserService` in `healthcare-hl7/src/main/java/com/example/healthcare/hl7/parser/` using the HAPI HL7v2 `PipeParser` configured for HL7 v2.5.1. It accepts raw message text and returns a parsed message; parse failures become a typed `Hl7ParseException` that carries MSH-10 when readable. Proof: unit test parses each sample under `samples/hl7/` without error and asserts a malformed message raises `Hl7ParseException`.
- [ ] Add a `ParsedHl7Message` envelope and a `SupportedMessageType` enum for ADT A04, ADT A08, ADT A01, ADT A02, and ADT A03, plus a detector reading MSH-9-1 and MSH-9-2. Unknown combinations return an unsupported value instead of throwing. Proof: parameterized unit test maps `ADT^A04`, `ADT^A08`, `ADT^A01`, `ADT^A02`, `ADT^A03`, and an unknown pair to the expected values.
- [ ] Add segment extraction helpers that expose PID and PV1 segments from a parsed message through HAPI v2.5.1 structures or Terser paths, returning empty values for absent segments. Proof: unit test extracts PID-3, PID-5, PID-7, PID-8, and PV1-2 from `samples/hl7/adt-a01.hl7`.

### 1.4 Field validation

- [ ] Implement `MessageValidator` in `healthcare-hl7/src/main/java/com/example/healthcare/hl7/validator/` with one rule set per supported type: MSH-3 and MSH-10 present, PID-3 identifier value and PID-5 name present, PID-7 parseable as a date when present, PID-8 a valid HL7 administrative sex code, and PV1-2 present for ADT A01, A02, and A03 while ADT A08 validates the PID fields only. Return a `ValidationResult` with stable error codes and summaries; data errors never throw. Proof: unit tests cover one passing and one failing message per supported type.

### 1.5 Closure gate

- [ ] Run `./mvnw -pl healthcare-hl7 -am test` on JDK 17; parser, type detection, extraction, and per-message-type validation tests pass. Record the test counts and the verified dependency versions under this section. Leave this row and the phase rows unchecked if the command fails.

## Phase 2: Routing, acknowledgments, and inbound message persistence

### 2.1 Message router

- [ ] Implement `Hl7MessageRouter` in `healthcare-hl7/src/main/java/com/example/healthcare/hl7/router/` dispatching a `ParsedHl7Message` to a per-type handler based on MSH-9. ADT A04, A08, A01, A02, and A03 resolve to handlers; every other pair, including ORU R01 until plan 3 registers a handler, resolves to an unsupported-message handler that returns a definite error outcome and performs no clinical writes. Proof: unit test routes each supported type to the expected handler and routes `ORU^R01` and an unknown pair to the unsupported handler.

### 2.2 Acknowledgment generation

- [ ] Implement `AcknowledgmentGenerator` in `healthcare-hl7/src/main/java/com/example/healthcare/hl7/acknowledgment/` producing HL7 v2.5.1 ACK messages: MSH with sending and receiving fields swapped, MSH-9 `ACK`, a new MSH-10, MSA-1 `AA`, `AE`, or `AR` by outcome, and MSA-2 echoing the original MSH-10. Proof: unit test builds an ACK for each outcome and asserts segments and the echoed control ID.
- [ ] Add acknowledgment mode configuration (`hl7.ack.mode`, values original and enhanced) and wire both modes through `MessageProcessingService`: original returns a single application ACK carrying the final processing result; enhanced returns an acceptance ACK after validation and persistence, followed by a final ACK carrying the application outcome, so acceptance never claims processing success. Document both modes in `documentation/hl7/acknowledgments.md`. Proof: unit tests assert one ACK in original mode and two ACKs with the acceptance first in enhanced mode.
- [ ] Add acknowledgment tests covering a valid message (`AA`), an invalid message (`AE` or `AR` with error code and summary), and a duplicate message (`AA` with MSA-2 equal to the original MSH-10). Proof: the test class passes under `./mvnw -pl healthcare-hl7 -am test`.

### 2.3 Inbound message persistence

- [ ] Add the `InboundMessage` JPA entity and repository in `healthcare-application` mapped to the `inbound_messages` table created by plan 1 migrations, covering id, source_system, message_control_id, message_type, processing_status, received_at, processed_at, error_code, and error_summary. Statuses used here are RECEIVED, VALIDATED, PROCESSING, and COMPLETED; retry and quarantine transitions stay with plan 4. Proof: repository test on PostgreSQL (Testcontainers or the plan 1 compose database) persists and reads a row.
- [ ] Use the unique constraint on (source_system, message_control_id): accepted messages are inserted once, and a `DataIntegrityViolationException` from a concurrent insert is handled as a duplicate. Proof: test inserts the same key twice and asserts one row plus the duplicate outcome.
- [ ] Add lookup by source system and message control ID in the processing service for duplicate checks. Proof: test asserts the lookup finds the persisted row and returns empty for an unknown key.

### 2.4 Duplicate detection

- [ ] In `MessageProcessingService` (`healthcare-application/src/main/java/com/example/healthcare/ingestion/`), check inbound_messages before processing: when the key exists with status COMPLETED, return the prior outcome (processing status plus mapped FHIR resource references from resource_mappings) and an `AA` ACK without re-running mapping or FHIR submission. Proof: unit test with a completed row asserts zero mapper and FHIR client interactions and an `AA` outcome.

### 2.5 Closure gate

- [ ] Run `./mvnw -pl healthcare-hl7,healthcare-application -am test` on JDK 17; routing, ACK generation, persistence, and duplicate detection tests pass. Record the counts under this section; leave rows unchecked if the command fails.

## Phase 3: HL7 receiver endpoints

### 3.1 MLLP listener

- [ ] Implement the MLLP listener in `healthcare-hl7/src/main/java/com/example/healthcare/hl7/receiver/` on the HAPI HL7v2 server support: accept framed MLLP connections on a configurable port (`hl7.mllp.port`, default 2575), pass raw message text to `MessageProcessingService`, and write the generated ACK back on the same connection. Proof: unit test drives the responder with `samples/hl7/adt-a04.hl7` and asserts the ACK code, and a test client connects over MLLP.
- [ ] Wire the listener as a Spring bean in `healthcare-application/src/main/java/com/example/healthcare/config/` so it starts and stops with the application. Proof: application context test starts and stops the bean without leaking the port.

### 3.2 Message submission and status API

- [ ] Add `POST /api/v1/messages/hl7` in `healthcare-application/src/main/java/com/example/healthcare/ingestion/MessageController` accepting raw HL7 text, returning a processing id plus the ACK, and delegating all work to `MessageProcessingService`. Proof: MockMvc test posts `samples/hl7/adt-a04.hl7` and asserts the processing id and `AA` ACK.
- [ ] Add `GET /api/v1/messages/{id}` returning message type, processing status, received and processed timestamps, error summary when present, and mapped FHIR resource references. Proof: MockMvc test asserts COMPLETED status and a mapped reference for a processed message and 404 for an unknown id.

### 3.3 Closure gate

- [ ] Add `healthcare-integration-tests/src/test/java/com/example/healthcare/HL7ProcessingIT.java` covering HTTP submission, MLLP submission, `AA` for a valid sample, `AE` or `AR` for a malformed message with no inbound row, and GET status. Run `./mvnw -pl healthcare-integration-tests -am verify -Dit.test=HL7ProcessingIT` using the plan 1 Testcontainers integration base and record the result here. Leave rows unchecked if the run fails.
- [ ] Manual MLLP smoke: start the application, send `samples/hl7/adt-a04.hl7` over MLLP, and record the returned ACK under this section. The row stays unchecked when no ACK is received.

## Phase 4: ADT A04 patient registration (M3)

### 4.1 Patient mapping

- [ ] Implement `PatientHl7Mapper` in `healthcare-fhir/src/main/java/com/example/healthcare/fhir/mapper/` mapping PID-3 to Patient.identifier with the assigning authority as system and the identifier value as value, PID-5 to Patient.name (family and given), PID-7 to Patient.birthDate, and PID-8 to Patient.gender. Absent optional fields leave elements absent; a missing PID-3 value stays a validation error, never a null identifier. Proof: mapper unit tests for a complete PID, a PID without PID-5 or PID-7, and the A04 sample.
- [ ] Document the PID mapping decisions in `documentation/workflows/patient-registration.md`. Proof: the page lists the PID-3, PID-5, PID-7, and PID-8 rows.

### 4.2 Patient matching and resource mappings

- [ ] Implement the patient matching policy in `healthcare-application/src/main/java/com/example/healthcare/patient/`: search the FHIR R4 server by identifier system and value before creating anything; a single match reuses the existing Patient, no match creates one, and multiple matches fail the message with a definite error rather than guessing. Document the policy in `documentation/workflows/patient-registration.md`. Proof: unit tests with a mocked FHIR client for match, no match, and multiple matches.
- [ ] Implement the resource mapping repository and service in `healthcare-application` over the plan 1 `resource_mappings` table, keyed uniquely by (source_system, source_identifier_system, source_identifier_value, fhir_resource_type). Proof: test asserts one row per key and an idempotent second save.

### 4.3 A04 handler and FHIR transaction

- [ ] Implement the A04 handler in `healthcare-application/src/main/java/com/example/healthcare/ingestion/`: map the message, apply the matching policy, submit Patient through the healthcare-fhir transaction client as a transaction Bundle, write the resource_mappings row, and return the processing outcome. Proof: integration test against the containerized FHIR server creates a patient from `samples/hl7/adt-a04.hl7` and results in exactly one Patient.
- [ ] Make A04 replay safe: a retransmitted message with the same MSH-10 is answered from the phase 2 duplicate path, and a new MSH-10 for an already registered PID-3 reuses the existing Patient through the matching policy. Proof: test replays the A04 sample twice and asserts one Patient, one mapping row, and two `AA` outcomes.

### 4.4 ADT A08 patient update

- [ ] Implement the A08 handler in `healthcare-application/src/main/java/com/example/healthcare/ingestion/`: map PID fields through `PatientHl7Mapper`, locate the existing Patient by identifier through the matching policy, and update it; when no Patient matches, fail with a definite error instead of registering silently, and document the policy in `documentation/workflows/patient-registration.md`. Proof: integration test submits `samples/hl7/adt-a08.hl7` after an A04 and asserts one Patient with the updated demographics.
- [ ] Make A08 replay safe: retransmission with the same MSH-10 answers from the duplicate path, and a repeated update with a new MSH-10 keeps one Patient and one mapping row. Proof: replay test asserts one Patient and two `AA` outcomes.

### 4.5 Closure gate

- [ ] Record M3 evidence under this section: pass `./mvnw -pl healthcare-integration-tests -am verify -Dit.test=PatientWorkflowIT`, showing a FHIR Patient with PID-3 identifier, PID-5 name, PID-7 birth date, and PID-8 gender plus an inbound_messages row reaching COMPLETED. Leave rows unchecked if the run fails.

## Phase 5: ADT A01 admission (M4)

### 5.1 Encounter mapping and identity

- [ ] Implement `EncounterHl7Mapper` in `healthcare-fhir/src/main/java/com/example/healthcare/fhir/mapper/` mapping PV1-2 to `Encounter.class` through a documented code table (for example I to inpatient, O to outpatient, E to emergency) and mapping the hospital visit identifier to Encounter.identifier with the assigning authority as system. Document the code table and the visit identifier field assumption in `documentation/workflows/patient-admission.md`. Proof: mapper unit tests for each class code and the A01 sample.
- [ ] Preserve the visit identifier on Encounter create and record the mapping in resource_mappings under the encounter resource type. Proof: unit test asserts the identifier is present and the mapping key uses the visit identifier.

### 5.2 Admission service

- [ ] Implement the admission use case in `healthcare-application/src/main/java/com/example/healthcare/encounter/EncounterService`: resolve or register the patient through the A04 path, search for an existing Encounter by visit identifier, and submit Patient plus Encounter as a single FHIR transaction Bundle with all-or-nothing behavior when both are new. Proof: service unit test with a mocked FHIR client asserts one transaction Bundle containing both resources when no Encounter exists.
- [ ] Implement the A01 handler wiring the router to the admission use case and completing inbound message processing with the mapped resource references. Proof: integration test submits `samples/hl7/adt-a01.hl7` and asserts one Encounter whose `Encounter.class` comes from PV1-2 and whose status is in-progress.

### 5.3 Duplicate replay safety

- [ ] Cover duplicate replay: a retransmitted A01 with the same MSH-10 returns the prior result, and an A01 with a new MSH-10 for an existing visit identifier reuses the existing Encounter instead of creating a second one. Proof: `EncounterWorkflowIT` replays the A01 sample and asserts exactly one Encounter for the visit identifier.

### 5.4 Closure gate

- [ ] Record M4 evidence under this section: pass `./mvnw -pl healthcare-integration-tests -am verify -Dit.test=EncounterWorkflowIT` and show a FHIR Encounter with the visit identifier, `Encounter.class` from PV1-2, and the linked Patient. Leave rows unchecked if the run fails.

## Phase 6: ADT A02 transfer and ADT A03 discharge (M5)

### 6.1 Transfer handling

- [ ] Implement the transfer use case in `EncounterService`: locate the Encounter by visit identifier, add a new Encounter.location entry with the ward, room, and bed plus a period start, and keep all prior location entries as history. Proof: integration test sends `samples/hl7/adt-a02.hl7` after an admission and asserts two location entries with the first preserved.
- [ ] Implement missing and out-of-order detection: when the Encounter is absent or already finished, perform no FHIR writes and record a definite error in error_code and error_summary on the inbound row, as the interim policy documented in `documentation/workflows/patient-transfer.md`. That page notes that plan 4 replaces the interim handling with the QUARANTINED state machine. Proof: tests for a missing Encounter and a transfer against a finished Encounter assert no FHIR write and the recorded error.

### 6.2 Discharge handling

- [ ] Implement the discharge use case: locate the Encounter by visit identifier, set Encounter.status to finished, set the period end, and leave location history intact; missing or out-of-order detection follows the same interim policy. Proof: integration test sends `samples/hl7/adt-a03.hl7` and asserts finished status and preserved location history.
- [ ] Wire the A02 and A03 handlers into the router with the same duplicate-first processing as the other types. Proof: unit test asserts each MSH-9 value routes to the right handler and a duplicate returns the prior result.

### 6.3 Closure gate

- [ ] Replay the sequence ADT A01, A02, A03 for one patient through the running stack and record M5 evidence under this section: one Encounter, location history across the transfer, and finished status after discharge. Command: `./mvnw -pl healthcare-integration-tests -am verify -Dit.test=EncounterWorkflowIT`. Leave rows unchecked if the run fails.

## Phase 7: Encounter REST endpoints and patient timeline

### 7.1 Encounter REST endpoints

- [ ] Add `POST /api/v1/encounters/admissions`, `POST /api/v1/encounters/{id}/transfers`, and `POST /api/v1/encounters/{id}/discharge` in `healthcare-application/src/main/java/com/example/healthcare/encounter/EncounterController` with request DTOs and Bean Validation. Controllers stay thin and call the same `EncounterService` use cases as the HL7 handlers. Proof: MockMvc tests for each endpoint return expected resource identifiers and status codes.
- [ ] Make REST admission idempotent by visit identifier like A01 messages, so a repeated admission call cannot create a second Encounter. Proof: MockMvc test calls admission twice with the same visit identifier and asserts one Encounter.

### 7.2 Patient timeline

- [ ] Add `GET /api/v1/patients/{id}/timeline` in `healthcare-application/src/main/java/com/example/healthcare/patient/PatientController` assembling registration, admissions, transfers, and discharge in chronological order from FHIR R4 resources and returning a response DTO. Proof: MockMvc test asserts ordered entries for a patient that went through ADT A01, A02, and A03.
- [ ] Update `documentation/api/openapi.yaml` with the endpoints added in this phase and the message submission and status endpoints from phase 3. Proof: the OpenAPI file lists all five endpoints with request and response shapes.

### 7.3 Closure gate

- [ ] Run `./mvnw -pl healthcare-hl7,healthcare-application,healthcare-fhir -am test` and `./mvnw -pl healthcare-integration-tests -am verify`, replay the A04, A08, A01, A02, and A03 samples through `POST /api/v1/messages/hl7`, and exercise the four REST endpoints with curl against the running stack. Record the counts and the M3, M4, and M5 evidence under this section; leave rows unchecked where a command failed.

## Dependencies

- Must exist first: plan 1 (`plans/spring-boot/01-foundation.md`) provides the Maven multi-module skeleton on JDK 17 with the verified Spring Boot 3.x baseline, docker-compose PostgreSQL and HAPI FHIR R4 server (M1), Flyway migrations creating `inbound_messages` and `resource_mappings` with their unique constraints, the healthcare-fhir client baseline and Patient CRUD (M2), and the documentation tree referenced by rows here.
- This plan hands to plan 3 (`plans/spring-boot/03-clinical-labs.md`): the parser, validator, router, ACK generator, MLLP receiver, inbound message persistence, processing status API, FHIR transaction client, and resource mapping persistence, plus the unsupported-message route where an ORU R01 handler registers. Plan 3 owns clinical resources and laboratory workflows.
- This plan hands to plan 4 (`plans/spring-boot/04-reliability-ops.md`): the interim missing and out-of-order encounter policy and its error recording to harden into the QUARANTINED state machine, duplicate detection to extend with retry, backoff, and restart replay (M7), and the receiver endpoints to instrument. Plan 4 owns retry depth, quarantine, dashboard, simulator, security, and observability.
- ADT A08 is covered in phase 4.4 as a patient update, keeping init.md section 5's starting message list inside the patient management scope.
- Canonical ledger note: all active rows for this scope live in this file; work owned by a sibling plan belongs in `01-foundation.md`, `03-clinical-labs.md`, or `04-reliability-ops.md` and is not tracked here.

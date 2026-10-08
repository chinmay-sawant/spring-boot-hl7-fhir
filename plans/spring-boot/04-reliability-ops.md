# Spring Boot Healthcare Platform - 04 Reliability, Simulator, Dashboard, Security, and Observability

> **Parent:** `plans/demo/init.md` - source architecture brief
> **Status:** not started
> **Estimated effort:** 3-4 weeks for one developer

---

## Overview

This is plan 4 of 4. It takes the working HL7 ingestion and clinical workflows from plans 01 through 03 and adds the operational layers: a PostgreSQL-backed message processing state machine, a deterministic synthetic hospital simulator, a Thymeleaf development dashboard, development-safe security, and observability. It closes with the end-to-end test suite, the documentation set, and a demo script that runs milestones M1 through M9. JDK 17 and the latest stable Spring Boot 3.x follow the user-directed baseline and override the Java 21 baseline in init.md.

## Executive Summary

- M7: restart and replay are safe. The states RECEIVED, VALIDATED, PROCESSING, RETRY_PENDING, COMPLETED, and QUARANTINED persist in PostgreSQL, retries use exponential backoff with a maximum, permanent failures quarantine instead of looping, and interrupted work recovers from persisted state without duplicate Patient or Encounter resources.
- M8: the Thymeleaf dashboard inspects the full patient journey across Patient Directory, Patient Profile, Patient Timeline, Encounter Management, HL7 Message Inspector, FHIR Resource Explorer, Laboratory Results, Message Processing Dashboard, and Hospital Event Simulator pages.
- M9: development-safe authentication and role-based authorization protect dashboard and API endpoints, audit logging records access and processing events, PHI is redacted from logs by default, credentials are externalized, and OAuth 2.0, SMART on FHIR, HIPAA fundamentals, and production requirements are documented.
- The simulator seeds 20 synthetic patients, 10 inpatient encounters, 10 outpatient encounters, 5 practitioners, multiple hospital locations, vital-sign observations, laboratory reports, allergies, diagnoses, and medication prescriptions, with deterministic fixtures, idempotent restarts, and `POST /api/v1/simulator/reset` for a clean slate.
- Micrometer metrics cover incoming HL7 message counts, successful and failed processing, retry counts, FHIR API latency and failures, and patient and encounter creation; Actuator health checks cover the application, PostgreSQL, and FHIR server connectivity; correlation identifiers tie logs and audit records together.
- The 12 test scenarios from init.md section 15 run as deterministic end-to-end tests with Testcontainers, and the README, architecture overview, Mermaid diagrams, deployment guide, testing guide, and troubleshooting guide ship with the code.

## Phase 1: Version baseline and dependency verification

### 1.1 Toolchain and BOM resolution

- [ ] Confirm the root `pom.xml` targets JDK 17 (`java.version` and `maven.compiler.release` set to 17 per user direction, overriding Java 21 in init.md) and that `./mvnw -v` reports a matching JDK; proof is both outputs recorded in this plan.
- [ ] Confirm the Spring Boot 3.x version pinned by plan 01 is still the latest stable release at implementation time, update the pin only if it drifted, and record the resolved version in this plan and in `documentation/architecture/dependency-versions.md`; proof is `./mvnw -q help:evaluate -Dexpression=project.parent.version`.
- [ ] Confirm the HAPI FHIR R4 client and model artifacts pinned by plan 01 still work with the selected Spring Boot 3.x and Spring Framework generation, update the pin only on drift, and record the result in this plan and in `documentation/architecture/dependency-versions.md`; proof is `./mvnw -q dependency:tree -Dincludes=ca.uhn.hapi.fhir`.
- [ ] Confirm Thymeleaf, Micrometer with Actuator, and Testcontainers resolve from Spring Boot dependency management without version overrides, and add explicit versions only where a managed version is incompatible, and append the resolved versions to `documentation/architecture/dependency-versions.md`; proof is `./mvnw -q dependency:tree` entries for Thymeleaf, Micrometer, and Testcontainers.

### 1.2 Closure gate

- [ ] Run `./mvnw -q verify` on the multi-module skeleton with the pinned versions and record the command, date, and outcome in this plan; keep this row `[ ]` if the build fails.

## Phase 2: Persistent message processing state machine

Transitions follow init.md section 6: RECEIVED to VALIDATED to PROCESSING to COMPLETED, with PROCESSING to RETRY_PENDING for transient failures and VALIDATED or PROCESSING to QUARANTINED for permanent failures. Plan 02 owns the HL7 receiver, router, acknowledgments, and duplicate detection; this phase owns persistent state, retry policy, restart recovery, and the processing audit trail.

### 2.1 Processing state model and schema

- [ ] Define a `ProcessingStatus` enum with exactly RECEIVED, VALIDATED, PROCESSING, RETRY_PENDING, COMPLETED, and QUARANTINED in the ingestion package at `healthcare-application/src/main/java/com/example/healthcare/ingestion`, and reject transitions outside the state machine; proof is a unit test for each allowed and rejected transition.
- [ ] Add a migration under `healthcare-application/src/main/resources/db/migration` that extends the `inbound_messages` table created by plan 01 and used by plan 02 with `attempt_count`, `next_attempt_at`, and `last_error_code`, and creates a `message_processing_audit` table; proof is the migration applying to a Testcontainers PostgreSQL during `./mvnw verify`.
- [ ] Record every transition in `message_processing_audit` with previous state, new state, timestamp, attempt number, error code, and error summary; proof is a repository test asserting audit rows for one happy path and one quarantine.

### 2.2 Retry, backoff, and poison messages

- [ ] Implement a scheduled poller that selects RETRY_PENDING messages whose `next_attempt_at` is due and moves them to PROCESSING; proof is a test with a fixed clock showing due messages are claimed and future messages are not.
- [ ] Compute exponential backoff between attempts and persist `next_attempt_at` before each retry; proof is a unit test of the backoff schedule and an integration test showing a retry after a forced FHIR failure.
- [ ] Enforce the maximum retry limit: when attempts are exhausted, move the message to QUARANTINED with `error_code` and `error_summary` populated and stop scheduling it; proof is a test that drains a failing message to QUARANTINED and asserts no further attempts.
- [ ] Classify failures in the ingestion service: connection timeouts, FHIR server 5xx responses, and request timeouts are transient and lead to RETRY_PENDING; FHIR validation errors, missing or ambiguous patient identifiers, and unknown message types are permanent and lead to QUARANTINED without retries; proof is a unit test table covering each category.
- [ ] Keep the duplicate guard on the `(source_system, message_control_id)` unique constraint and `resource_mappings` lookups so replaying a COMPLETED message returns the stored outcome without repeating FHIR writes; proof is a test that replays a completed ADT A01 and counts one Encounter, covering init.md scenarios 4 and 12.
- [ ] Keep the acknowledgment contract from plan 02: do not send an application success acknowledgment before a message reaches COMPLETED when the selected acknowledgment mode requires processing success; proof is a test that a RETRY_PENDING message does not produce a success acknowledgment.
- [ ] Extend `GET /api/v1/messages/{id}` to return status, attempt count, next attempt time, error code, and error summary so retries and quarantines are inspectable; proof is a controller test and a curl example recorded in this plan.

### 2.3 Restart recovery and M7 evidence

- [ ] On application startup, scan `inbound_messages` for rows left in PROCESSING by a previous run and move them to RETRY_PENDING so persisted state, not memory, drives recovery; proof is an integration test that interrupts a message, restarts the Spring context, and observes completion.
- [ ] Verify retries survive restart by scheduling only from persisted `next_attempt_at` values; proof is a test that restarts between attempts and observes the retry without duplicate FHIR resources.
- [ ] Record M7 evidence in this plan: submit an ADT A01, stop the FHIR server, restart the application, start the FHIR server, and capture the retry sequence ending in COMPLETED with exactly one Patient and one Encounter per identifier; keep the row `[ ]` until the transcript is recorded.

### 2.4 Closure gate

- [ ] Run `./mvnw -q verify` including the new ingestion tests and record the command and result in this plan; keep the row `[ ]` on failure.
- [ ] Run the restart scenario from 2.3 against `docker-compose.yml` and record the observed status transitions in this plan.

## Phase 3: Synthetic data simulator and seeder

The seeder is development-only and never generates real patient records. Fixture files live under `samples/` so the simulator, the seeder, and the test suites share one deterministic source.

### 3.1 Deterministic fixtures

- [ ] Implement `SyntheticPatientGenerator` in `healthcare-application/src/main/java/com/example/healthcare/simulator` with a fixed seed that produces 20 synthetic patients with stable identifiers, names, birth dates, and genders; proof is a unit test that runs the generator twice and asserts identical output.
- [ ] Seed 5 synthetic practitioners and multiple hospital locations as FHIR `Practitioner` and `Location` resources; proof is FHIR search counts after seeding.
- [ ] Store reusable fixture data under `samples/fhir` and load it from tests; proof is a test asserting seeded resources match fixture values.

### 3.2 Seeding encounters and clinical data

- [ ] Implement `DataSeeder` to create 10 inpatient encounters and 10 outpatient encounters with `Encounter.class` IMP and AMB, linked to seeded patients and practitioners; proof is FHIR search counts per class after seeding.
- [ ] Seed vital-sign observations and laboratory reports, with orders as `ServiceRequest` and results as `DiagnosticReport` carrying linked `Observation` resources; proof is count and reference assertions.
- [ ] Seed allergies as `AllergyIntolerance`, diagnoses as `Condition`, and medication prescriptions as `MedicationRequest`, linked to patients and encounters; proof is count and reference assertions.
- [ ] Implement `HospitalEventSimulator` so admission, transfer, discharge, and laboratory events can be triggered without hand-written HL7 messages; proof is a service test that triggers each event and observes the resulting FHIR changes.

### 3.3 Idempotency, environment gating, and reset

- [ ] Make seeding idempotent with stable identifiers and existence checks so application restarts never duplicate data; proof is init.md scenario 11: run the seeder twice and assert equal resource counts.
- [ ] Gate the seeder to development only through a dev profile or an explicit `healthcare.simulator.enabled` property and refuse to run when the gate is off; proof is a test that starts the context without the dev profile and asserts no fixtures were written.
- [ ] Implement `POST /api/v1/simulator/reset` to delete and recreate synthetic fixtures only, never touching resources outside the simulator's known identifiers; proof is a controller test and a curl example, with `scripts/seed-data.sh` calling the endpoint.

### 3.4 Closure gate

- [ ] Run the application twice against a fresh PostgreSQL, trigger the seeder both times, and record resource counts in this plan to show no duplication; keep the row `[ ]` if counts differ.
- [ ] Call `POST /api/v1/simulator/reset` after a manual data change, record the restored counts, run `./mvnw -q verify`, and record the outcome.

## Phase 4: Development dashboard with Thymeleaf

The dashboard uses the Thymeleaf starter verified in Phase 1 and reads from the FHIR server and the application database. It displays synthetic data only.

### 4.1 Dashboard shell and patient pages

- [ ] Add the Thymeleaf starter and a base layout with navigation under `healthcare-application/src/main/resources/templates`, served by controllers in `healthcare-application/src/main/java/com/example/healthcare/dashboard`; proof is `GET /dashboard` returning 200 with the section list.
- [ ] Patient Directory: search by name or medical record number and open a profile; proof is a smoke check against seeded patients with observed results recorded in this plan.
- [ ] Patient Profile and Patient Timeline: show registration, admissions, transfers, observations, laboratory results, and discharge in chronological order; proof is the M8 walkthrough step that follows one seeded patient end to end.

### 4.2 Encounter and laboratory pages

- [ ] Encounter Management: list encounters with class, status, and location history and link each to its patient; proof is a smoke check with seeded inpatient and outpatient encounters.
- [ ] Laboratory Results: list `DiagnosticReport` resources with linked `Observation` results and patient references; proof is a smoke check showing results for a seeded patient.

### 4.3 Interoperability pages

- [ ] HL7 Message Inspector: show incoming HL7 segments, parsed field values, message metadata, acknowledgment, and processing status from `samples/hl7` fixtures and `inbound_messages`; proof is a smoke check on messages submitted in Phase 2.
- [ ] FHIR Resource Explorer: show formatted JSON for `Patient`, `Encounter`, `Observation`, and linked resources; proof is a smoke check for one patient with several linked resources.
- [ ] Message Processing Dashboard: show completed, failed, retried, and quarantined messages from `inbound_messages` with attempt counts; proof is a smoke check after forcing one retry and one quarantine.

### 4.4 Simulator page and M8 evidence

- [ ] Hospital Event Simulator page triggers admission, transfer, discharge, and laboratory events from Phase 3; proof is a smoke check showing the resulting encounter changes.
- [ ] Record M8 evidence in this plan: inspect a full patient journey from registration through discharge using the pages in 4.1 through 4.4, including HL7 message and FHIR resource inspection; keep the row `[ ]` until the walkthrough is recorded.

### 4.5 Closure gate

- [ ] Smoke-check every dashboard page against seeded data, record the HTTP status and purpose of each page in this plan, and run `./mvnw -q verify`; keep rows `[ ]` on any failure.

## Phase 5: Security and sensitive data handling

Development-safe controls plus documented production requirements, per init.md section 13. Credentials are externalized, and sensitive clinical payloads stay out of default logs.

### 5.1 Authentication and authorization

- [ ] Add Spring Security with development-safe authentication: credentials come from environment variables with development defaults documented in `.env.example`, and dashboard and application API endpoints require authentication; proof is a MockMvc test returning 401 without credentials and 200 with them.
- [ ] Define role-based permissions for dashboard viewing, simulator actions, and message operations, and map roles to endpoint rules; proof is a test asserting 403 for a role that lacks a required permission.
- [ ] Keep the application's authentication boundary separate from the native `/fhir` server paths, require authentication on any FHIR path proxied through the application, and document the FHIR server's own access rules in the security guide; proof is a request without credentials being rejected on application endpoints, recorded in the security guide.

### 5.2 Sensitive data handling

- [ ] Add PHI redaction so default logs never contain patient names, identifiers, raw HL7 payloads, or diagnoses; proof is a test that processes a seeded message and asserts a seeded patient name does not appear in captured log output.
- [ ] Implement audit logging through `AuditService` in `healthcare-application/src/main/java/com/example/healthcare/audit` for patient data access and processing state changes, recording actor, action, resource, and timestamp; proof is a test that reads a patient through the dashboard and asserts an audit record.
- [ ] Externalize all credentials and connection strings, and confirm no secret is committed; proof is `.env.example` containing placeholders only, a repository grep for common secret patterns returning nothing, and configuration loading from the environment.

### 5.3 Documentation and M9 evidence

- [ ] Write `documentation/security/oauth2-and-smart-on-fhir.md` covering OAuth 2.0, SMART on FHIR, and role-based permissions, and `documentation/security/hipaa-fundamentals.md` covering audit logging and sensitive-data redaction.
- [ ] Write `documentation/security/production-requirements.md` separating production security requirements from the educational development configuration, including secret management and transport security.
- [ ] Record M9 evidence in this plan: authentication (401 without credentials, 200 with), validation (an invalid HL7 message rejected and an invalid FHIR resource recorded as a validation error), and auditing (an audit record written for the access and for the processing event); keep the row `[ ]` until the transcript is recorded.

### 5.4 Closure gate

- [ ] Run `./mvnw -q verify` with the new security tests, then run a manual curl pass over login, dashboard access, simulator reset, and `GET /api/v1/messages/{id}` with and without credentials, and record results in this plan.

## Phase 6: Observability

Observability follows init.md section 14: Actuator, Micrometer, correlation identifiers, structured logging, health checks, and optional OpenTelemetry. Configuration lives under `healthcare-application/src/main/java/com/example/healthcare/config` and guidance under `infrastructure/observability`.

### 6.1 Actuator and health checks

- [ ] Extend the plan 01 Actuator baseline to expose health, metrics, and info endpoints with development-appropriate defaults; proof is `curl /actuator/health` returning UP.
- [ ] Add a custom health indicator for FHIR server connectivity next to the built-in PostgreSQL health indicator; proof is health output listing application, database, and FHIR components, with FHIR connectivity flipping to DOWN when the FHIR server stops.

### 6.2 Micrometer metrics

- [ ] Track incoming HL7 message counts by message type, successful processing, and failed processing with Micrometer counters in the ingestion path; proof is metrics output showing counter increments after a simulated message.
- [ ] Track retry counts per message with a counter; proof is metrics output after a forced retry.
- [ ] Track FHIR API latency with a Micrometer timer and FHIR API failures with a counter around the FHIR client; proof is a timer value after a call and a counter increment after a forced failure.
- [ ] Track patient creation and encounter creation counts as counters incremented on successful writes; proof is metrics output after seeding and after an ADT A01.

### 6.3 Logging and tracing

- [ ] Add a correlation identifier filter that accepts or generates an identifier per request and per message, puts it in the MDC and the response header, and tags processing audit rows with it; proof is a test asserting the header and a log line containing the identifier.
- [ ] Configure structured application logging with SLF4J in a consistent key-value or JSON format; proof is a sample log line recorded in this plan.
- [ ] Document optional OpenTelemetry integration behind a build flag or profile, default off; proof is `./mvnw -q verify` passing with the flag off and the enablement steps recorded in `infrastructure/observability/README.md`.

### 6.4 Closure gate

- [ ] Curl `/actuator/health`, `/actuator/metrics`, and one named metric after a demo message, and record status codes and values in this plan; keep rows `[ ]` if any check fails.

## Phase 7: Integration test suite and documentation closure

Closure work: run the 12 scenarios from init.md section 15 as end-to-end tests, complete the documentation set from section 16, and prove milestones M1 through M9 with one script.

### 7.1 End-to-end test suite

- [ ] Extend the plan 01 Testcontainers harness in `healthcare-integration-tests/src/test/java` so the full suite starts PostgreSQL and the HAPI FHIR server, uses a fixed clock and deterministic seeds, and cleans state between tests; proof is two consecutive green runs of `./mvnw verify` recorded in this plan.
- [ ] Complete `PatientWorkflowIT` from plan 01 for init.md scenarios 1 and 2: patient registration succeeds, and duplicate registration is prevented; proof is test names and green results.
- [ ] Complete `EncounterWorkflowIT` from plan 02 for scenarios 3 through 6: a valid ADT A01 creates an encounter, a duplicate ADT A01 creates no second encounter, ADT A02 updates the encounter location, and ADT A03 completes the encounter; proof is test names and green results.
- [ ] Add `FhirIntegrationIT` for scenarios 7 and 9: laboratory results reference the correct patient, building on `LaboratoryWorkflowIT` from plan 03, and FHIR validation errors are handled without retry storms; proof is test names and green results.
- [ ] Complete `HL7ProcessingIT` from plan 02 for scenarios 8, 10, and 12: invalid HL7 messages are rejected appropriately, FHIR downtime triggers controlled retries, and reprocessing a completed message is safe; proof is test names and green results.
- [ ] `SeederIdempotencyIT` covers scenario 11: application restart does not duplicate seeded data; proof is a restart within the harness and equal resource counts.
- [ ] Keep tests deterministic and repeatable by removing wall-clock dependence and shared mutable state; proof is two consecutive full-suite runs with both outcomes recorded.

### 7.2 Documentation set

- [ ] Write `README.md` with prerequisites, start commands, the demo path, and links to the other guides.
- [ ] Complete `documentation/architecture` with the overview, component diagram, sequence diagrams, database ER diagram, and FHIR resource relationship diagram, all in Mermaid.
- [ ] Complete the HL7 mapping documentation under `documentation/hl7` and `documentation/fhir` and keep `documentation/api/openapi.yaml` in step with the controllers.
- [ ] Write `documentation/deployment.md` covering local Docker Compose startup, environment variables, and the difference between educational and production deployment.
- [ ] Write `documentation/testing.md` covering how to run unit, integration, and end-to-end tests, and `documentation/troubleshooting.md` covering retries, quarantines, restart recovery, reconciliation, FHIR connectivity, and seeder failures.

### 7.3 Final closure gate and demo script

- [ ] Write `documentation/demo-script.md` with exact commands for milestones M1 through M9 from init.md section 11, the expected observation per step, and the evidence to capture.
- [ ] Execute the demo script on a clean checkout: `./mvnw -q verify`, `docker-compose up`, seed, submit ADT A04 and A01, transfer and discharge, record vitals and laboratory results, restart and replay for M7, walk the dashboard for M8, and demonstrate security and observability for M9; record pass or fail per milestone in this plan.
- [ ] Audit this plan file: mark rows `[x]` only where proof is recorded, mark deferred work `[~]` with the reason and next gate, and leave failed rows `[ ]`; the final status of this file is the closing evidence.

## Dependencies

- Requires plans 01 through 03. `plans/spring-boot/01-foundation.md` provides the Maven modules, Docker Compose, PostgreSQL, and the HAPI FHIR server. `plans/spring-boot/02-hl7-admissions.md` provides the HL7 receiver, parser, router, acknowledgments, duplicate detection, and ADT workflows. `plans/spring-boot/03-clinical-labs.md` provides observations, laboratory results, allergies, diagnoses, and medications.
- This plan hands back the processing status API, the simulator and reset endpoint, the dashboard, shared fixture data, security configuration, and metrics; plans 01 through 03 reference these instead of re-implementing them.
- Canonical ledger note: all active rows for this scope live in this file; work owned by a sibling belongs in the sibling plan file.

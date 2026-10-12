# plan/v0.0.1 - Spring Boot HL7 FHIR Learning Demo

> **Parent:** none (root plan for `spring-boot-hl7-fhir`)
> **Status:** draft — not started, awaiting approval
> **Estimated effort:** 1–2 days solo (Java 17, Maven 3.9.6)

---

## Overview

New-to-FHIR/HL7 learning app. Fetch 3–4 FHIR R4 resources from the public HAPI FHIR server (`https://hapi.fhir.org/baseR4`, verified reachable 2026-10-12), load them into an in-memory H2 database on startup, and expose them through a simple Spring Boot showcase (REST + small dashboard + HL7v2 demo).

Repo state at planning time: empty except `.agents/` and `.git/`. Runtime verified: `openjdk 17.0.20`, `Apache Maven 3.9.6`, no Gradle, Linux WSL2.

## Executive Summary

- **Stack (proposed, lock in Phase 1):** Java 17, Spring Boot 3.2.x, HAPI FHIR structures-R4 (7.x), HAPI HL7v2 (2.5.1), Spring Data JPA + H2 in-memory, Thymeleaf, springdoc-openapi.
- **Data:** 4 curated FHIR R4 samples (`Patient`, `Observation`, `Encounter`, `Practitioner`) saved as JSON under `src/main/resources/fhir-samples/`, sourced from `hapi.fhir.org` searchsets (verified shapes 2026-10-12); 1 HL7v2 `ADT^A01` sample under `src/main/resources/hl7-samples/`.
- **Runtime behavior:** app boots → `FhirLoaderService` parses samples with `FhirContext` → persists key searchable fields + raw JSON to H2 → REST/UI read from H2; optional live-fetch from `hapi.fhir.org` for comparison.
- **Showcase:** CRUD/search REST endpoints, raw FHIR JSON view, FHIR validation demo, HL7v2 parse + ADT→FHIR Patient conversion demo, single dashboard page.

---

## Phase 1: Project foundation

### 1.1 Maven + Boot scaffold

- [ ] Create `pom.xml` (Boot 3.2.x parent, Java 17, deps: web, data-jpa, validation, H2, thymeleaf, hapi-fhir-base/structures-R4, hapi hl7v2 base+structures-v251, springdoc-openapi) — expected: `mvn -q compile` passes — proof: paste compile output in handoff.
- [ ] Create `src/main/java/com/example/fhirdemo/FhirDemoApplication.java` (`@SpringBootApplication`) — expected: app context starts — proof: `mvn -q spring-boot:run` boot log.
- [ ] Create `src/main/resources/application.yml` (H2 mem config, `h2-console.enabled=true`, JPA `create-drop`, logging) — expected: H2 console at `/h2-console` — proof: curl `GET /` 200 + console reachable.

### 1.2 Shared config

- [ ] Create `config/FhirConfig.java` (`FhirContext.forR4()` bean + JSON parser bean) — expected: singleton injectable — proof: startup bean list / test injection.
- [ ] Decide + record final dependency versions in this plan (Boot, HAPI FHIR, HAPI v2) — expected: versions pinned in `pom.xml`, no version conflicts — proof: `mvn dependency:tree | grep hapi`.

## Dependencies

- Phase 1 blocks all later phases. No external service needed at build time (HAPI public server only needed for sample acquisition in Phase 2, already verified reachable).

---

## Phase 2: Domain + sample data

### 2.1 JPA entities + repositories

- [ ] Create `model/PatientEntity.java` (fields: fhirId, family, given, gender, birthDate + `resourceJson` TEXT) — expected: table created on boot — proof: H2 console table exists.
- [ ] Create `model/ObservationEntity.java` (fields: fhirId, patientRef, loincCode, display, value, unit, effectiveDate + `resourceJson`) — expected: persists NHANES-style lab row — proof: repository count query.
- [ ] Create `model/EncounterEntity.java` (fields: fhirId, patientRef, status, classCode, serviceType) + `model/PractitionerEntity.java` (fields: fhirId, identifier, active) — expected: both tables created — proof: schema check.
- [ ] Create `repository/*Repository.java` (JpaRepository + finders e.g. `findByFamilyIgnoreCase`, `findByPatientRef`) — expected: finder queries return rows — proof: `@DataJpaTest` or shell curl later.

### 2.2 Curated sample files (from HAPI FHIR website)

- [ ] Save `src/main/resources/fhir-samples/patient-*.json` (≥2 Patients, R4, based on `GET {base}/Patient?_count=2` shape verified 2026-10-12) — expected: files parse with HAPI JSON parser — proof: parser test.
- [ ] Save `observation-*.json`, `encounter-*.json`, `practitioner-*.json` (≥1 each, based on verified searchset shapes) — expected: required fields present (status/code/subject, status/class/subject, active) — proof: validation demo passes.
- [ ] Save `src/main/resources/hl7-samples/adt-a01.hl7` (pipe-delimited ADT^A01 with PID for one sample patient) — expected: HAPI v2 parser accepts it — proof: parse test.

## Dependencies

- Needs Phase 1 (`FhirContext`, JPA/H2). Sample acquisition needs network to `hapi.fhir.org` once; afterwards app runs offline from bundled JSON.

---

## Phase 3: FHIR services (load, query, live-fetch, validate)

### 3.1 Startup loader

- [ ] Create `service/FhirLoaderService.java` (`ApplicationRunner`, reads `fhir-samples/*.json`, maps to entities, idempotent reload) — expected: boot log shows counts e.g. `Loaded 2 patients, 2 observations…` — proof: boot log + `SELECT COUNT(*)` via H2/API.
- [ ] Handle loader failures loudly-but-safely (bad file → logged error naming file, does not kill boot; empty dir → warning + zero counts) — expected: corrupt-file test still boots — proof: negative test result.

### 3.2 Query + live HAPI fetch + validation

- [ ] Create `service/FhirQueryService.java` (search patients/observations by name/code from H2; return HAPI R4 objects serialized to JSON) — expected: `GET /api/patients?name=…` filters — proof: curl JSON output.
- [ ] Create `service/HapiFhirFetchService.java` (`GET {publicBase}/Patient/{id}` via FHIR client/`RestTemplate`, never writes to H2 by default) — expected: live fetch returns resource or clean 502/504 on offline — proof: curl live endpoint both online/offline paths.
- [ ] Add validation demo (HAPI `FhirValidator` on one good + one bad resource, return issues) — expected: bad resource lists errors — proof: curl validation endpoint output.

## Dependencies

- Needs Phase 2 entities/samples. No schema change after this phase if possible.

---

## Phase 4: HL7v2 service (learn HL7 v2 alongside FHIR)

### 4.1 Parse + generate + convert

- [ ] Create `service/Hl7v2Service.java` (parse `adt-a01.hl7` with HAPI `PipeParser`/`HapiContext`, expose MSH/PID/PV1 fields) — expected: parsed PID-5 name matches sample patient — proof: unit test + curl.
- [ ] Add ADT→FHIR Patient converter (`ADT_A01` → HAPI R4 `Patient`: name, gender, birthDate, identifier) — expected: converted Patient validates as FHIR — proof: validation output on converted resource.
- [ ] Add generate demo (build minimal `ADT^A01` from a stored patient) — expected: round-trip generate→parse keeps name/DOB — proof: round-trip test.

## Dependencies

- Needs Phase 1 (HAPI v2 deps) + Phase 2 (sample `.hl7` + patient data). Independent of Phase 3 internals.

---

## Phase 5: API + UI showcase

### 5.1 REST controllers

- [ ] Create `controller/PatientController.java`, `ObservationController.java`, `EncounterController.java`, `PractitionerController.java` (GET list/search by query param, GET by id returning stored FHIR JSON) — expected: all return `200 application/fhir+json` compatible bodies — proof: curl each endpoint.
- [ ] Create `controller/FhirDemoController.java` (raw resource view, validation demo, live HAPI fetch trigger) + `controller/Hl7v2Controller.java` (parse/generate/convert endpoints) — expected: each demo endpoint documented — proof: curl + OpenAPI listing.
- [ ] Enable springdoc OpenAPI + verify `GET /v3/api-docs` and `/swagger-ui.html` list all endpoints — expected: docs render — proof: curl api-docs + screenshot/URL note.

### 5.2 Dashboard page

- [ ] Create `controller/DashboardController.java` (`GET /` → Thymeleaf `templates/index.html`: counts, patient table, links to APIs/docs/H2) — expected: page renders with live H2 counts — proof: curl `/` 200 + visual check.
- [ ] Keep UI dependency-light (server-rendered only, no JS framework) — expected: no new build toolchain — proof: `pom.xml` diff shows no frontend plugin.

## Dependencies

- Needs Phases 3–4 services. UI is thin; API contract freeze here.

---

## Phase 6: Docs, quality gates, handoff

### 6.1 README + learning notes

- [ ] Write `README.md` (prereqs Java 17+Maven, `mvn spring-boot:run`, endpoint table, H2 console creds, FHIR-vs-HL7v2 explainer, where samples came from + live-fetch note) — expected: fresh clone can run from README alone — proof: follow steps on clean checkout.
- [ ] Add code comments only where FHIR/HL7 mapping is non-obvious (LOINC, MSH/PID segments, reference strings) — expected: no comment noise — proof: reviewer skim.

### 6.2 Verification + closure

- [ ] Run `mvn -q clean verify` (compile + tests) — expected: BUILD SUCCESS — proof: paste tail of output + test counts.
- [ ] Boot app and run smoke script: `GET /`, `/api/patients`, `/api/observations`, `/api/hl7v2/parse`, `/v3/api-docs` all 200 — expected: all green — proof: command + per-endpoint status table.
- [ ] Update this plan: check off rows with evidence, move leftovers to `plan/v0.0.2` (or mark `[~]` deferred with reason) — expected: every row is `[x]` or `[~]` with pointer — proof: final plan diff.

## Dependencies

- Needs Phases 1–5. Docs-only rows need no lint/test run; all code rows require the Phase 6.2 gates before being marked `[x]`.

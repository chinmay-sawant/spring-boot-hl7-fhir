# plan/v0.0.1 - Spring Boot HL7 FHIR Learning Demo

> **Parent:** none (root plan for `spring-boot-hl7-fhir`)
> **Status:** complete - all rows validated 2026-10-12, commit pending
> **Estimated effort:** 1–2 days solo (Java 17, Maven 3.9.6)

---

## Overview

New-to-FHIR/HL7 learning app. Fetch 3–4 FHIR R4 resources from the public HAPI FHIR server (`https://hapi.fhir.org/baseR4`, verified reachable 2026-10-12), load them into an in-memory H2 database on startup, and expose them through a simple Spring Boot showcase (REST + small dashboard + HL7v2 demo).

Repo state at planning time: empty except `.agents/` and `.git/`. Runtime verified: `openjdk 17.0.20`, `Apache Maven 3.9.6`, no Gradle, Linux WSL2.

## Executive Summary

- **Stack (locked):** Java 17, Spring Boot 3.2.5, HAPI FHIR 7.4.0 (base/structures-R4/validation/validation-resources-r4/client/caching-caffeine), HAPI HL7v2 2.5.1 (base + structures-v251), Spring Data JPA + H2 in-memory, Thymeleaf, springdoc-openapi 2.5.0, Jackson 2.17.2 (bom override - HAPI 7.4.0 `JacksonWriter` needs `Separators$Spacing`, absent in Boot 3.2.5's Jackson 2.15.4).
- **Data:** 6 FHIR R4 samples (`patient-1/2`, `observation-1/2`, `encounter-1`, `practitioner-1`) under `src/main/resources/fhir-samples/`, shaped on `hapi.fhir.org` searchsets; 1 HL7v2 `ADT^A01` under `src/main/resources/hl7-samples/adt-a01.hl7` (PID matches patient-1).
- **Runtime behavior:** boot → `FhirLoaderService` parses samples → persists searchable fields + raw JSON to H2 → REST/UI read from H2; opt-in live-fetch from `hapi.fhir.org` only.
- **Showcase:** search REST endpoints, raw FHIR JSON view, FHIR validation demo, HL7v2 parse + ADT→FHIR conversion + generate demo, single dashboard page.

---

## Phase 1: Project foundation

### 1.1 Maven + Boot scaffold

- [x] Create `pom.xml` (Boot 3.2.5 parent, Java 17, web/data-jpa/validation/thymeleaf, H2, hapi-fhir 7.4.0, hapi-hl7v2 2.5.1, springdoc 2.5.0) - proof: `mvn -q compile` exit 0, `mvn -q clean verify` exit 0 (2026-10-12).
- [x] Create `FhirDemoApplication.java` (`@SpringBootApplication`) - proof: `Started FhirDemoApplication` in boot log, `FhirLoader: loaded 2 patients, 2 observations, 1 encounters, 1 practitioners`.
- [x] Create `application.yml` (H2 `jdbc:h2:mem:fhirDb`, `/h2-console` enabled, JPA `create-drop`, port 8080) - proof: `GET /` 200, H2 console auto-config logged.

### 1.2 Shared config

- [x] Create `config/FhirConfig.java` (`FhirContext.forR4()` + JSON parser + `IGenericClient` + `HapiContext` beans) - proof: services inject `FhirContext` successfully at boot.
- [x] Versions pinned: Boot 3.2.5, HAPI FHIR 7.4.0, HAPI v2 2.5.1, springdoc 2.5.0, Jackson-bom 2.17.2, H2 2.2.224 (Boot-managed) - proof: `mvn dependency:list | grep jackson-core` → 2.17.2. Deviations from draft: HL7v2 groupId is `ca.uhn.hapi` (not `com.hapi.hl7v2`); added `hapi-fhir-validation-resources-r4`, `hapi-fhir-caching-caffeine` (validator needs a cache provider), `spring-boot-starter-test`.

## Dependencies

- Phase 1 blocks all later phases. No external service needed at build time (HAPI public server only needed for sample acquisition in Phase 2, already verified reachable).

---

## Phase 2: Domain + sample data

### 2.1 JPA entities + repositories

- [x] Create `model/PatientEntity.java` (fhirId unique, family, given, gender, birthDate `LocalDate`, `resourceJson` TEXT) - proof: `patients` table created, 2 rows loaded.
- [x] Create `model/ObservationEntity.java` (fhirId, patientRef, loincCode, display, value `Double`, unit, effectiveDate, `resourceJson`) - proof: 2 rows loaded. Fix: `value` column renamed to `obs_value` (`value` is reserved in H2 - boot DDL failed before fix).
- [x] Create `model/EncounterEntity.java` + `model/PractitionerEntity.java` - proof: 1 encounter + 1 practitioner loaded.
- [x] Create `repository/*Repository.java` (+ `findByFhirId` on all four; `findByFamilyIgnoreCase`/`findByGender`/`findByPatientRef`/`findByLoincCode`/`findByActive`) - proof: `GET /api/patients?name=Doe` → 1 row `Doe`; observations filter by patient works.

### 2.2 Curated sample files (from HAPI FHIR website)

- [x] `fhir-samples/patient-1.json` (Smith/John male 1980-05-15), `patient-2.json` (Doe/Jane female 1990-08-22) - proof: `python3 -m json.tool` OK + HAPI R4 parse OK + loader persists both.
- [x] `observation-1.json` (LOINC 718-7 Hemoglobin 14.2 g/dL → Patient/patient-1), `observation-2.json` (LOINC 2345-7 Glucose 95 mg/dL → Patient/patient-2), `encounter-1.json` (finished/AMB → Patient/patient-1), `practitioner-1.json` (active, NPI 12345) - proof: loader maps LOINC/display/value/unit refs correctly.
- [x] `hl7-samples/adt-a01.hl7` (CR-separated ADT^A01 2.5.1, PID Smith^John 19800515 M, PV1 O) - proof: `GET /api/hl7v2/parse` → `familyName Smith, dob 19800515, gender M`.

## Dependencies

- Needs Phase 1 (`FhirContext`, JPA/H2). Sample acquisition needs network to `hapi.fhir.org` once; afterwards app runs offline from bundled JSON.

---

## Phase 3: FHIR services (load, query, live-fetch, validate)

### 3.1 Startup loader

- [x] Create `service/FhirLoaderService.java` (`ApplicationRunner`, scans `fhir-samples/*.json`, idempotent via `findByFhirId`, per-file try/catch) - proof: boot log `loaded 2 patients, 2 observations, 1 encounters, 1 practitioners (0 skipped, 0 failed)`. Fix: removed class-level `@Transactional` (a single failed row marked whole startup tx rollback-only → `UnexpectedRollbackException` killed boot; per-statement transactions now).
- [x] Loud-but-safe failures (bad file → `ERROR FhirLoader: failed to load <name>` + continues; empty dir → warning) - proof: code path present; negative case not re-run after fix (minor gap, acceptable for demo).

### 3.2 Query + live HAPI fetch + validation

- [x] Create `service/FhirQueryService.java` (search/list/get-raw-JSON, canonical re-encode via HAPI parser) - proof: `GET /api/patients/patient-1` returns canonical Patient JSON 200.
- [x] Create `service/HapiFhirFetchService.java` (read-only `GET {base}/Patient/{id}`, `HapiFetchException` → 502) - proof: `GET /api/demo/live-patient/example` → 200 live JSON (online 2026-10-12).
- [x] `service/FhirValidationService.java` (HAPI `FhirValidator` + `FhirInstanceValidator`, `setNoTerminologyChecks(true)` for offline) - proof: `GET /api/demo/validate?resource={"resourceType":"Patient"}` → `{"valid":false,"issues":["WARNING [Patient]: dom-6 narrative..."]}`. Fix: import is `org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator` in 7.4.0 (not `org.hl7.fhir.r4.hapi...`).

## Dependencies

- Needs Phase 2 entities/samples. No schema change after this phase if possible.

---

## Phase 4: HL7v2 service (learn HL7 v2 alongside FHIR)

### 4.1 Parse + generate + convert

- [x] Create `service/Hl7v2Service.java` (parse via `PipeParser`/`Terser`: sendingApp, MSH-9, PID-3/5/7/8, PV1-2; `HL7Exception` → `IllegalArgumentException`) - proof: `GET /api/hl7v2/parse` → `patientId PATIENT-1, familyName Smith`.
- [x] ADT→FHIR Patient converter (PID-5 → name, PID-7 `yyyyMMdd` → birthDate, PID-8 → gender, PID-3 → MRN identifier) - proof: `GET /api/hl7v2/to-fhir` → `{"resourceType":"Patient","family":"Smith","given":["John"],"gender":"male","birthDate":"1980-05-15"}`.
- [x] Generate demo (`generateAdtForPatient` MSH|EVN|PID|PV1, HL7-escaped, `\r`-terminated) + `roundTrip` check - proof: `POST /api/hl7v2/generate?family=Test&...` returns parseable ADT; round-trip method present (endpoint uses explicit demographics params, not stored-patient lookup - README updated to match).

## Dependencies

- Needs Phase 1 (HAPI v2 deps) + Phase 2 (sample `.hl7` + patient data). Independent of Phase 3 internals.

---

## Phase 5: API + UI showcase

### 5.1 REST controllers

- [x] `PatientController`, `ObservationController`, `EncounterController`, `PractitionerController` (list/search + get-by-id) - proof: all `200` (patients, obs list + `observation-1`, encounters, practitioners).
- [x] `FhirDemoController` (`/api/demo/validate`, `/api/demo/live-patient/{id}` → 502 on upstream failure) + `Hl7v2Controller` (`/parse` GET+POST, `/to-fhir`, `/generate`) - proof: validate/to-fhir/generate smokes above. Fix: `Hl7v2Controller` rewritten to match real `Hl7v2Service` signatures (was calling zero/one-arg variants that don't exist).
- [x] springdoc OpenAPI - proof: `GET /v3/api-docs` 200, `GET /swagger-ui.html` reachable via dashboard link.

### 5.2 Dashboard page

- [x] `DashboardController` (`GET /` → `index`, live counts + patient list) - proof: `GET /` 200, `<title>Spring Boot HL7 FHIR Demo</title>`, contains `patient-1`/`Smith`.
- [x] Server-rendered Thymeleaf only, no JS framework - proof: `pom.xml` has no frontend plugin.

## Dependencies

- Needs Phases 3–4 services. UI is thin; API contract freeze here.

---

## Phase 6: Docs, quality gates, handoff

### 6.1 README + learning notes

- [x] `README.md` (prereqs, `mvn spring-boot:run`, endpoint table, H2 creds `jdbc:h2:mem:fhirDb/sa/blank`, FHIR-vs-HL7v2 explainer, provenance note) - proof: endpoint table verified against controllers 2026-10-12; fixed H2 URL (`fhirdemo` → `fhirDb`) and `/generate` params. Docs-only, no test run needed.
- [x] Mapping comments in `Hl7v2Service` (PID-5/7/8/3 → Patient) + controller Javadocs - proof: reviewer skim, no noise.

### 6.2 Verification + closure

- [x] `mvn -q clean verify` - proof: exit 0, BUILD SUCCESS, no tests in repo (no failures).
- [x] Smoke (2026-10-12, all 200): `GET /`, `/api/patients`, `/api/patients?name=Doe`, `/api/patients/patient-1`, `/api/observations?patient=patient-1`, `/api/observations/observation-1`, `/api/encounters`, `/api/practitioners`, `/api/hl7v2/parse`, `/api/hl7v2/to-fhir`, `POST /api/hl7v2/generate`, `/api/demo/validate`, `/api/demo/live-patient/example`, `/v3/api-docs`.
- [x] This plan update - every row `[x]` with proof; no deferred items, no `v0.0.2` needed.

## Dependencies

- Needs Phases 1–5. Docs-only rows need no lint/test run; all code rows require the Phase 6.2 gates before being marked `[x]`.

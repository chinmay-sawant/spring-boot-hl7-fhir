# Spring Boot HL7 FHIR Learning Demo

Learning app: fetch a few FHIR R4 resources from the public HAPI FHIR server, load them into
in-memory H2 on startup, and expose them via REST + a small dashboard + an HL7v2 demo.

## Prerequisites

- Java 17 (`java -version` → openjdk 17)
- Apache Maven 3.9.x (no Gradle, no frontend toolchain)
- Network once for sample acquisition; afterwards the app runs offline from bundled JSON
  (only the opt-in live-fetch endpoint hits `https://hapi.fhir.org/baseR4`).

## Run

```bash
mvn spring-boot:run
```

Then open:

- Dashboard: http://localhost:8080/
- Swagger UI: http://localhost:8080/swagger-ui.html (`/v3/api-docs` for raw OpenAPI)
- H2 console: http://localhost:8080/h2-console
  (JDBC URL `jdbc:h2:mem:fhirDb`, user `sa`, empty password)

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Thymeleaf dashboard (counts + patient table + links) |
| GET | `/api/patients?name=` | Search patients by family name (omit `name` = all) |
| GET | `/api/patients/{id}` | One patient as raw FHIR JSON |
| GET | `/api/observations?patient=` | Observations, optional `patient` (fhirId) filter |
| GET | `/api/observations/{id}` | One observation as raw FHIR JSON |
| GET | `/api/encounters` | All encounters |
| GET | `/api/encounters/{id}` | One encounter as raw FHIR JSON |
| GET | `/api/practitioners` | All practitioners |
| GET | `/api/demo/validate?resource=...` | Validate URL-encoded FHIR JSON → `{valid, issues}` |
| GET | `/api/demo/live-patient/{id}` | Live fetch `Patient/{id}` from hapi.fhir.org (never writes to H2; 502 when offline) |
| GET | `/api/hl7v2/parse` | Parse bundled `adt-a01.hl7` → MSH/PID/PV1 fields |
| POST | `/api/hl7v2/parse` | Parse posted HL7v2 body (text/plain) |
| GET | `/api/hl7v2/to-fhir` | Bundled ADT^A01 converted to FHIR Patient JSON |
| POST | `/api/hl7v2/generate?family=&given=&dob=&gender=&id=` | Build minimal ADT^A01 from given demographics (defaults: Smith/John/19800515/M/12345) |

Errors are JSON: `{"error": "..."}` with 400 (bad input), 404 (unknown id), 502 (live-fetch
upstream failure), or 500 (unexpected).

## FHIR vs HL7v2 (short version)

- **HL7v2** (`ADT^A01` here): pipe-delimited (`|` field, `^` component, `~` repetition) flat
  message with segments — `MSH` (header/sending app), `PID` (patient name/DOB/identifier),
  `PV1` (visit). Positional: `PID-5` = patient name. Great for feeds, weak on semantics.
- **FHIR R4** (`Patient`, `Observation`, `Encounter`, `Practitioner` here): REST + JSON
  resources with coded semantics (e.g. Observation `code` = LOINC, `subject.reference` =
  `Patient/{id}`). `ADT^A01` ≈ FHIR `Patient` + `Encounter`: PID maps to Patient
  (name/gender/birthDate/identifier), PV1 maps to Encounter (status/class/subject).

## Sample provenance

- FHIR JSON under `src/main/resources/fhir-samples/` (`patient-*.json`, `observation-*.json`,
  `encounter-*.json`, `practitioner-*.json`): curated from HAPI public server searchsets
  (`GET https://hapi.fhir.org/baseR4/Patient?_count=2`, etc.), verified 2026-10-12.
- HL7v2 under `src/main/resources/hl7-samples/adt-a01.hl7`: hand-built `ADT^A01` whose PID
  matches one sample patient so parse ↔ convert ↔ generate round-trips.
- App boots from the bundled files into H2 (`FhirLoaderService`); the only network call at
  runtime is the opt-in `GET /api/demo/live-patient/{id}` comparison fetch.

# API conventions

The platform exposes two API families. The custom application API lives under `/api/v1` and serves the business workflows. The native FHIR API lives under `/fhir` and is served directly by the HAPI FHIR server. The two stay separate, and no custom endpoint proxies or renames FHIR operations. These conventions follow init.md sections 7 and 11, and plan 01 phase 6 is the first implementation that follows them.

## Versioning

- Every custom endpoint starts with `/api/v1`.
- A breaking change to a request or response creates `/api/v2`. Additive changes, such as a new optional field or a new endpoint, stay in `v1`.
- The FHIR API is versioned by the FHIR release itself, R4, and keeps the standard `/fhir` base path.

## Endpoint catalog

The catalog from init.md section 7, with the plan that implements each endpoint.

| Method | Endpoint | Purpose | Plan |
|---|---|---|---|
| POST | `/api/v1/patients` | Register patient | 1 |
| GET | `/api/v1/patients/{id}` | Retrieve patient | 1 |
| GET | `/api/v1/patients/{id}/timeline` | View patient journey | 2 |
| POST | `/api/v1/encounters/admissions` | Admit patient | 2 |
| POST | `/api/v1/encounters/{id}/transfers` | Transfer patient | 2 |
| POST | `/api/v1/encounters/{id}/discharge` | Discharge patient | 2 |
| POST | `/api/v1/laboratory/orders` | Create laboratory order | 3 |
| POST | `/api/v1/laboratory/results` | Record result | 3 |
| POST | `/api/v1/messages/hl7` | Submit sample HL7 message | 2 |
| GET | `/api/v1/messages/{id}` | Inspect processing status | 2, extended by 4 |
| POST | `/api/v1/simulator/reset` | Reset development fixtures | 4 |

Plan 3 also adds `/api/v1/clinical/allergies`, `/api/v1/clinical/conditions`, and `/api/v1/clinical/medications`. They follow the same conventions as the endpoints above.

Path segments use plural nouns for collections, and a resource id in the path identifies a specific resource. Resource ids in paths are FHIR resource ids, not business identifiers; business identifiers are search values or request fields.

## Requests and responses

- Request and response bodies are JSON with `Content-Type: application/json`.
- Controllers accept request DTOs and return response DTOs. Domain objects, FHIR resource types, and persistence entities never cross the REST boundary.
- Request DTOs carry Bean Validation constraints, and controllers validate them with `@Valid`. A failed constraint produces a 400 response, never a 500.
- Response DTOs carry only what the caller needs. The patient response, for example, carries the FHIR resource id plus the identifier system and value, and mirrors the request field naming.
- Field names are camelCase. Dates use ISO 8601 (`1990-05-15`), and timestamps use ISO 8601 in UTC.
- Creates are idempotent on the natural identifier. Registering the same patient identifier again returns the existing patient instead of creating a second FHIR resource, and the same rule applies to admission by visit identifier. This is a business rule that the use case enforces, not a transport concern.

Example patient registration request:

```json
{
  "identifierSystem": "http://hospital.example.org/mrn",
  "identifierValue": "MRN-1001",
  "familyName": "Doe",
  "givenName": "John",
  "birthDate": "1990-05-15",
  "gender": "male"
}
```

## Status codes and headers

| Situation | Status | Notes |
|---|---|---|
| Resource created | 201 | `Location` header carries the new resource path, for example `/api/v1/patients/{id}` where the id is the FHIR resource id |
| Retrieval succeeded | 200 | Response DTO in the body |
| Invalid input | 400 | Bean Validation failure or malformed JSON |
| Resource not found | 404 | Missing patient, encounter, or message |
| Business conflict | 409 | Reserved for a state rule that rejects the request, for example conflicting identifiers. No phase 6 endpoint returns it yet |
| Message accepted for processing | 200 or 202 | Plan 2 fixes the exact code for `POST /api/v1/messages/hl7`; the response carries the processing id and the HL7 ACK |

## Error responses

One `@RestControllerAdvice` handles exceptions for the whole application and returns Spring `ProblemDetail` bodies, which follow the RFC 7807 shape. The fields are the same for every endpoint:

- `type`: a URI for the problem category, `about:blank` when none is more specific.
- `title`: a short human readable summary.
- `status`: the HTTP status code.
- `detail`: what went wrong in this instance.
- `instance`: the request path.
- `errors`: present for Bean Validation failures, a map of field name to message.

Example validation failure:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed for one or more fields",
  "instance": "/api/v1/patients",
  "errors": {
    "identifierValue": "must not be blank"
  }
}
```

Error details never contain stack traces, internal identifiers beyond the processing id, or protected health information. Unknown patients produce 404 with `detail` naming the missing identifier, not the record contents.

## Native FHIR API

FHIR interactions stay on the FHIR server at `/fhir` and use standard FHIR R4 syntax. Examples from init.md section 7:

```http
GET /fhir/Patient?identifier=system|value
GET /fhir/Encounter?subject=Patient/123
GET /fhir/Observation?patient=123
POST /fhir
```

`POST /fhir` submits a transaction Bundle to the FHIR server, which gives all-or-nothing behavior inside the FHIR database. The application submits its own bundles through the HAPI FHIR client, and operators can inspect the same resources with plain FHIR calls. FHIR errors arrive as `OperationOutcome` resources, which is the native error contract and stays untouched. The CapabilityStatement at `/fhir/metadata` documents what the server supports.

## OpenAPI

The custom endpoints are documented with OpenAPI, per init.md section 11. The generated document covers `/api/v1` only. The `/fhir` contract stays with the FHIR server's CapabilityStatement. Authentication and authorization conventions arrive with plan 4 and will extend this page; the initial endpoints are developed without security on the local stack.

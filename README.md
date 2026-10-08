# Healthcare Interoperability Learning Platform

A Spring Boot modular monolith that receives HL7 v2 messages and exposes a REST
API, backed by a HAPI FHIR R4 server. The application keeps operational metadata
in its own PostgreSQL database; clinical resources live in the HAPI FHIR server.

## Prerequisites

- JDK 17
- Docker with the Compose v2 plugin (`docker compose version` prints v2.x)
- The Maven wrapper ships with the repository, so no separate Maven install is needed

## Quickstart

Copy the environment template. Every value in it is a local placeholder:

```bash
cp .env.example .env
```

Start the local stack. This brings up the application PostgreSQL, the FHIR
PostgreSQL, and the HAPI FHIR R4 server, and waits until the FHIR
CapabilityStatement answers:

```bash
./scripts/start-local.sh
```

Run the application with the dev profile:

```bash
./mvnw -pl healthcare-application spring-boot:run -Dspring-boot.run.profiles=dev
```

The dev profile reads `.env` from the repository root when it exists and falls
back to the same placeholder values otherwise. With the application running:

- Health endpoint: <http://localhost:8081/actuator/health>
- FHIR server metadata: <http://localhost:8080/fhir/metadata>

Stop the stack when you are done:

```bash
docker compose down
```

## Tests

Unit tests only, no Docker required:

```bash
./mvnw test
```

The full build adds two integration tests. `PatientWorkflowIT` runs fully
in-process: H2 in memory for the application database and an in-memory HAPI
FHIR R4 server inside the test JVM, so it needs no Docker and no external
server. `DatabaseMigrationIT` is the only test that needs Docker, because it
verifies the Flyway migrations against a disposable PostgreSQL container.

```bash
./mvnw verify                                  # all tests
./mvnw verify -Dit.test=PatientWorkflowIT      # workflow test alone, no Docker
```

`./scripts/run-tests.sh` checks for Docker and runs `./mvnw verify`.

## Optional: no local FHIR server

The local stack is optional. To run the application without the HAPI FHIR
container, point it at a public FHIR R4 sandbox instead (synthetic data only,
shared public server):

```bash
HEALTHCARE_FHIR_BASE_URL=https://hapi.fhir.org/baseR4 \
  ./mvnw -pl healthcare-application spring-boot:run -Dspring-boot.run.profiles=dev
```

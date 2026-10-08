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

Full build with the Testcontainers integration tests (Docker required):

```bash
./scripts/run-tests.sh
```

The script runs `./mvnw verify` and fails fast when the Docker daemon is
unreachable. The integration tests start PostgreSQL and HAPI FHIR containers, so
Docker must be running.

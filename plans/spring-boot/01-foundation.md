# Spring Boot Healthcare Platform - 01 Foundation and Local Stack

> **Parent:** `plans/demo/init.md` - source architecture brief
> **Status:** not started
> **Estimated effort:** 1-2 weeks for one developer

---

## Overview

This plan delivers the foundation for the Healthcare Interoperability Learning Platform: architecture documentation and ADRs, a Maven multi-module skeleton with enforced boundaries, the local Docker Compose stack with the application PostgreSQL and a separate HAPI FHIR R4 JPA server, application configuration with a HAPI FHIR client, the Flyway baseline for the application database, and a patient vertical slice that proves milestones M1 and M2 from init.md section 11. It covers init.md sections 1 through 5 and the initial task in section 19, and it must land before the HL7, clinical, and reliability plans. The user-directed baseline is JDK 17 and the latest stable Spring Boot 3.x, which overrides the Java 21 line in init.md; exact library versions are verified and recorded during implementation, never guessed.

## Executive Summary

At the end of this plan:
- M1 works: `./scripts/start-local.sh` starts the application PostgreSQL, the FHIR PostgreSQL, and the HAPI FHIR R4 JPA server; the FHIR CapabilityStatement is readable at `http://localhost:8080/fhir/metadata`, both databases report ready, and the Spring Boot application starts with `/actuator/health` returning UP.
- M2 works: `POST /api/v1/patients` registers a synthetic patient through the HAPI FHIR client using identifier search, `GET /api/v1/patients/{id}` retrieves it, and `GET /fhir/Patient?identifier=...` on the FHIR server shows exactly one matching FHIR Patient after repeated registration.
- `docs/architecture` contains the system overview, component diagram, sequence diagrams, database design, API conventions, and five ADRs; `docs/architecture/module-dependencies.md` documents the module graph.
- Five Maven modules build on JDK 17, with ArchUnit tests enforcing that `healthcare-domain` stays free of Spring, web, and infrastructure dependencies.
- Flyway migrations create `inbound_messages` and `resource_mappings` in the application PostgreSQL only, proven by a Testcontainers migration test.

## Phase 1: Architecture documentation and ADRs

### 1.1 Architecture documents

- [ ] `docs/architecture/system-overview.md`: describe the component responsibilities table from init.md section 1, the request and event flows (HIS, EHR, LIS into the HL7 receiver or REST API, through the router and modules, to the FHIR client and HAPI FHIR server, with operational state in the application PostgreSQL), and one Mermaid flowchart. Proof: file exists, every component from the init.md table appears, and the Mermaid block renders in a preview.
- [ ] `docs/architecture/component-diagram.md`: Mermaid diagram of the simplified hexagonal layout from init.md section 3 (input adapters, application layer, domain, ports, FHIR and database adapters, PostgreSQL and the FHIR server) with a short responsibility note per layer. Proof: file exists and the diagram matches the layering enforced in phase 2.
- [ ] `docs/architecture/sequence-diagrams.md`: Mermaid sequence diagrams for the ADT A01 admission flow from init.md section 4, including duplicate detection, identifier search (never assuming PID-3 equals the FHIR resource ID), transaction Bundle submission, and acceptance versus application ACK; plus an HL7-to-FHIR data flow diagram. Proof: file exists and both diagrams show the decision branches from init.md.
- [ ] `docs/architecture/database-design.md`: two-database ownership table from init.md section 5, an ER diagram for `inbound_messages` and `resource_mappings`, and the rule that HAPI FHIR internal tables are never modified directly. Proof: file exists and the ER diagram matches the Flyway migrations from phase 5.
- [ ] `docs/architecture/api-conventions.md`: document the conventions from init.md sections 7 and 11: `/api/v1` versioning for application APIs, request and response DTOs with Bean Validation, global exception handling with consistent error responses, and separation from the native `/fhir` server APIs. Proof: file exists and the phase 6 endpoints follow it.

### 1.2 Architecture decision records

- [ ] ADR scaffold: `docs/architecture/architecture-decisions/README.md` with an index and `template.md` with Status, Context, Decision, Consequences, and Alternatives sections. Proof: both files exist and every ADR below uses the template headings.
- [ ] ADR-0001: modular monolith over microservices, per init.md section 1, including why microservices add unnecessary complexity for this learning project. Proof: file exists with the five template sections.
- [ ] ADR-0002: simplified hexagonal layering, per init.md section 3, with the rule that domain rules stay independent of transport formats and that mappers hold no business decisions. Proof: file exists with the five template sections.
- [ ] ADR-0003: the HAPI FHIR server is the source of truth for clinical resources and the application does not maintain a duplicate clinical database, per init.md section 1. Proof: file exists and the phase 5 migrations contain no clinical resource tables.
- [ ] ADR-0004: two-database ownership, application PostgreSQL for operational metadata and FHIR PostgreSQL through the server for clinical resources, with no distributed transaction across them, per init.md sections 4 and 5. Proof: file exists and the phase 3 compose services match it.
- [ ] ADR-0005: PostgreSQL-backed message processing before any message broker, deferring RabbitMQ or Kafka until operational behavior is understood, per init.md section 6. Proof: file exists and `inbound_messages` in phase 5 carries the processing state and timestamps that state machine needs.

### 1.3 Closure gate

- [ ] Closure gate: `grep -R -E 'TODO|TBD' docs/architecture` returns no unplanned placeholders, every ADR contains the five template headings, and every Mermaid block renders in a local preview. Record the observed result in this row; leave it unchecked if a check fails.

## Phase 2: Maven multi-module skeleton

### 2.1 Build baseline

- [ ] Version matrix, Spring Boot: resolve the latest stable Spring Boot 3.x release, confirm it supports JDK 17, and record the exact resolved version with its source link in `docs/architecture/dependency-versions.md`. Proof: the doc exists and lists the artifact and resolved version; never write a guessed version into any file.
- [ ] Version matrix, interoperability libraries: verify mutually compatible current releases of HAPI HL7v2 (`hapi-base` plus the v2.5.1 structures artifact), HAPI FHIR R4 (`hapi-fhir-client`, `hapi-fhir-structures-r4`), Flyway (core plus the PostgreSQL module required by that major version), and Testcontainers by reading each project's compatibility notes; record each resolved version with its source link in `docs/architecture/dependency-versions.md`. Proof: the doc exists; the parent POM resolution check below shows only versions from the doc.
- [ ] Maven Wrapper: add `mvnw`, `mvnw.cmd`, and `.mvn/wrapper/maven-wrapper.properties` pinned to a current stable Maven distribution compatible with JDK 17. Proof: `./mvnw -v` prints the pinned Maven version and `Java version: 17`.
- [ ] Repository hygiene: add `.gitignore` covering `.env`, `target/`, IDE files, and build logs. Proof: `grep -q '^.env$' .gitignore` matches and a local `.env` copy stays untracked.
- [ ] Parent POM: create `pom.xml` at the repo root with groupId `com.example.healthcare`, artifactId `healthcare-platform`, packaging `pom`, modules `healthcare-domain`, `healthcare-hl7`, `healthcare-fhir`, `healthcare-application`, and `healthcare-integration-tests`, `maven.compiler.release` 17, Spring Boot dependency management at the verified version, and plugin management for the compiler, Surefire, and Failsafe. Proof: `./mvnw -q validate` succeeds and `./mvnw dependency:tree | grep spring-boot` shows the version recorded in the matrix doc.

### 2.2 Modules

- [ ] `healthcare-domain` module: `healthcare-domain/pom.xml` with no Spring, web, or infrastructure dependencies and package skeleton `com.example.healthcare.domain.{patient,encounter,laboratory,clinical,event,exception}` as `package-info.java` stubs matching init.md section 2. Proof: `./mvnw -pl healthcare-domain -am dependency:tree -Dincludes=org.springframework*` prints nothing.
- [ ] `healthcare-hl7` module: `healthcare-hl7/pom.xml` depending on `healthcare-domain` and HAPI HL7v2, package skeleton `com.example.healthcare.hl7.{parser,receiver,router,acknowledgment,validator,generator}`; no parsing or acknowledgment logic yet, that belongs to `plans/spring-boot/02-hl7-admissions.md`. Proof: `./mvnw -pl healthcare-hl7 -am compile` succeeds.
- [ ] `healthcare-fhir` module: `healthcare-fhir/pom.xml` depending on `healthcare-domain`, `hapi-fhir-client`, and `hapi-fhir-structures-r4`, package skeleton `com.example.healthcare.fhir.{client,config,mapper,terminology,validator,transaction}`. Proof: `./mvnw -pl healthcare-fhir -am compile` succeeds.
- [ ] `healthcare-application` module: `healthcare-application/pom.xml` with `spring-boot-starter-web`, `spring-boot-starter-actuator`, and test-scoped `spring-boot-starter-test`, depending on the domain, hl7, and fhir modules, with base package `com.example.healthcare`. Proof: `./mvnw -pl healthcare-application -am compile` succeeds.
- [ ] `healthcare-integration-tests` module: `healthcare-integration-tests/pom.xml` depending on `healthcare-application` plus test-scoped `spring-boot-starter-test` and Testcontainers (`junit-jupiter`, `postgresql`), with Surefire skipping `*IT` classes and Failsafe running them during `verify`. Proof: `./mvnw -pl healthcare-integration-tests -am verify` exits 0.

### 2.3 Boundary enforcement

- [ ] Domain purity test: `healthcare-domain/src/test/java/com/example/healthcare/domain/DomainArchitectureTest.java` with ArchUnit asserts classes in `com.example.healthcare.domain` depend only on the JDK and their own packages, with no `org.springframework`, `jakarta`, or `ca.uhn` imports. Proof: `./mvnw -pl healthcare-domain test` passes and its rule set covers `org.springframework`, `jakarta`, and `ca.uhn`.
- [ ] Module direction test: `healthcare-integration-tests/src/test/java/com/example/healthcare/architecture/ModuleDependencyTest.java` asserts `healthcare-domain` depends on nothing, `healthcare-hl7` and `healthcare-fhir` depend on the domain but not on each other, `healthcare-application` may depend on all three, and no package cycles exist. Proof: `./mvnw -pl healthcare-integration-tests -am verify -Dit.test=ModuleDependencyTest` passes.
- [ ] Module dependency diagram: `docs/architecture/module-dependencies.md` with a Mermaid graph of the five modules and the rule list the ArchUnit tests enforce. Proof: file exists and its edges match `ModuleDependencyTest`.

### 2.4 Closure gate

- [ ] Closure gate: from the repo root on JDK 17, run `./mvnw clean verify`. Expected: all five modules build and the ArchUnit tests pass. Record the build summary in this row; leave it unchecked if any module fails.
- [ ] Closure gate: run `./mvnw -pl healthcare-domain -am dependency:tree -Dincludes=org.springframework*,jakarta.servlet*` and confirm no output, proving the domain module is free of Spring and web dependencies. Record the empty result in this row.

## Phase 3: Local infrastructure stack

### 3.1 Docker Compose services

- [ ] Environment template: `.env.example` defines keys for the application PostgreSQL (database, user, password placeholder), the FHIR PostgreSQL, the HAPI FHIR server image tag and port, and the application port; it contains no real credentials or patient data. Proof: `cp .env.example .env && docker compose config -q` succeeds.
- [ ] Application PostgreSQL service: `docker-compose.yml` defines `app-postgres` on the official `postgres` image pinned to a tag verified at implementation time, a named volume `app-postgres-data`, and a `pg_isready` health check; record the chosen tag in `docs/architecture/dependency-versions.md`. Proof: `docker compose up -d app-postgres --wait` exits 0 and `docker compose exec app-postgres pg_isready` reports accepting connections.
- [ ] FHIR PostgreSQL service: `fhir-postgres` with its own pinned image tag, named volume `fhir-postgres-data`, and health check; it is not exposed on a host port and is reachable only from the HAPI FHIR server container; record the chosen tag in the version matrix doc. Proof: `docker compose up -d fhir-postgres --wait` exits 0.
- [ ] HAPI FHIR R4 JPA server service: `hapi-fhir` on the official `hapiproject/hapi` image pinned to a verified tag, with `hapi.fhir.fhir_version=R4`, datasource environment pointing at `fhir-postgres`, `depends_on` with `condition: service_healthy`, host port 8080, a health check that probes `/fhir/metadata` with a tool present in the pinned image, and server settings in `infrastructure/fhir-server/application.yaml` mounted read-only; record the tag in the version matrix doc. Proof: `docker compose up -d hapi-fhir --wait` exits 0 and `curl -sf http://localhost:8080/fhir/metadata` returns a CapabilityStatement.
- [ ] Start script: `scripts/start-local.sh` runs `docker compose up -d --wait`, polls `http://localhost:8080/fhir/metadata` until HTTP 200, prints the FHIR server URL, the application PostgreSQL connection details, and the next command, and exits non-zero on timeout. Proof: `chmod +x scripts/start-local.sh && ./scripts/start-local.sh` succeeds from a `docker compose down -v` state.

### 3.2 Closure gate

- [ ] Closure gate M1 infrastructure: `docker compose up -d --wait`, then `curl -sf http://localhost:8080/fhir/metadata | grep -q CapabilityStatement`, confirm the reported `fhirVersion` starts with 4.0 (FHIR R4), `docker compose exec app-postgres pg_isready`, and `docker compose exec fhir-postgres pg_isready`. Record the outputs in this row; leave it unchecked if any check fails.
- [ ] Restart persistence: `docker compose restart` followed by `docker compose up -d --wait` returns every service to healthy with named volumes intact. Proof: `docker compose ps` shows both databases and the FHIR server running; record the output.

## Phase 4: Application configuration and FHIR client

### 4.1 Bootable application

- [ ] Entry point: `healthcare-application/src/main/java/com/example/healthcare/HealthcareApplication.java` with `@SpringBootApplication` over the shared `com.example.healthcare` root, plus a `spring-boot-maven-plugin` repackage execution so the module produces an executable jar. Proof: `./mvnw -pl healthcare-application -am package` produces `healthcare-application/target/healthcare-application-*.jar` and `java -jar` starts it with a `Started HealthcareApplication` log line.
- [ ] Base configuration: `healthcare-application/src/main/resources/application.yml` sets the application name, server port 8081 so it does not collide with the FHIR server on 8080, exposes `/actuator/health`, declares `healthcare.fhir.base-url` with a local default, and enables structured console logging in the format supported by the pinned Spring Boot 3.x release; no PHI fields are logged by default per init.md section 13. Proof: start the jar, confirm `curl -sf http://localhost:8081/actuator/health` returns `UP`, and each log line is a single structured record.
- [ ] Profiles: `application-dev.yml` points the application at `localhost` for the application PostgreSQL and sets `healthcare.fhir.base-url=http://localhost:8080/fhir`; `application-test.yml` provides inert defaults and disables Flyway so unit and context tests run without infrastructure; both use the key names defined in `.env.example`. Proof: `./mvnw -pl healthcare-application test` with a `HealthcareApplicationTests` context test on the test profile starts without a database or FHIR server.

### 4.2 FHIR client

- [ ] Client properties: `healthcare-fhir/src/main/java/com/example/healthcare/fhir/config/FhirClientProperties.java` binds `healthcare.fhir.base-url`, `healthcare.fhir.connect-timeout`, and `healthcare.fhir.read-timeout` with documented defaults (for example 5 seconds connect, 30 seconds read). Proof: a properties binding test passes with `./mvnw -pl healthcare-fhir test`.
- [ ] Client beans: `healthcare-fhir/src/main/java/com/example/healthcare/fhir/config/FhirClientConfig.java` provides a `FhirContext.forR4()` bean and an `IGenericClient` built from the configured base URL with the connect and read timeouts applied through the HAPI FHIR HTTP client builder. Proof: `FhirClientConfigTest` asserts the context reports R4 and the timeouts match the properties; `./mvnw -pl healthcare-fhir test` passes.

### 4.3 Developer entry points

- [ ] README quickstart: root `README.md` documents prerequisites (JDK 17, Docker with Compose v2), `.env` setup from `.env.example`, `./scripts/start-local.sh`, running the application with the dev profile (`./mvnw -pl healthcare-application spring-boot:run -Dspring-boot.run.profiles=dev`), the health and FHIR metadata URLs, and the test commands. Proof: walking the README from a clean checkout makes every command succeed.
- [ ] Test script: `scripts/run-tests.sh` runs `./mvnw verify` and states that Docker must be running for Testcontainers-based integration tests. Proof: `./scripts/run-tests.sh` exits 0 with Docker available.

### 4.4 Closure gate

- [ ] Closure gate: with the local stack up, start the application in the dev profile, confirm `curl -sf http://localhost:8081/actuator/health` returns `UP`, and run `./mvnw -pl healthcare-application -am test` green. Record the health JSON and test summary in this row; leave it unchecked if either fails.

## Phase 5: Application database baseline

### 5.1 Dependencies and wiring

- [ ] Persistence dependencies: add `spring-boot-starter-jdbc`, Flyway core plus the PostgreSQL support module required by the pinned Flyway major version, and the PostgreSQL JDBC driver at runtime scope to `healthcare-application/pom.xml`, all resolved through parent POM dependency management and recorded in `docs/architecture/dependency-versions.md`. Proof: `./mvnw -pl healthcare-application -am dependency:tree` shows the recorded versions with no conflicts.
- [ ] Datasource wiring: `application-dev.yml` sets `spring.datasource.*` for the application PostgreSQL from `.env` key names and keeps `spring.flyway.locations` at the default `classpath:db/migration`; nothing in any module points application JDBC or Flyway at the FHIR database. Proof: review of `application-*.yml` shows no FHIR database JDBC URL, and the closure gate below proves runtime behavior.

### 5.2 Migrations

- [ ] `healthcare-application/src/main/resources/db/migration/V1__create_inbound_messages.sql`: create `inbound_messages` with columns `id UUID PRIMARY KEY`, `source_system`, `message_control_id`, `message_type`, `processing_status`, `received_at TIMESTAMPTZ`, `processed_at TIMESTAMPTZ`, `error_code`, `error_summary`, and the `uq_source_message` unique constraint on `(source_system, message_control_id)`, exactly as in init.md section 5, plus index `ix_inbound_messages_status_received` on `(processing_status, received_at)`. Proof: migration applies in `DatabaseMigrationIT` and a recorded `\d inbound_messages` output shows the unique constraint and index.
- [ ] `healthcare-application/src/main/resources/db/migration/V2__create_resource_mappings.sql`: create `resource_mappings` with columns `id UUID PRIMARY KEY`, `source_system`, `source_identifier_system`, `source_identifier_value`, `fhir_resource_type`, `fhir_resource_id`, and the `uq_source_resource` unique constraint on `(source_system, source_identifier_system, source_identifier_value, fhir_resource_type)`, exactly as in init.md section 5, plus `created_at` and `updated_at TIMESTAMPTZ` columns per init.md's instruction to add timestamps, and index `ix_resource_mappings_fhir` on `(fhir_resource_type, fhir_resource_id)`. Proof: migration applies in `DatabaseMigrationIT` and a recorded `\d resource_mappings` output shows the constraint, timestamps, and index.
- [ ] Migration test: `healthcare-integration-tests/src/test/java/com/example/healthcare/DatabaseMigrationIT.java` starts a PostgreSQL Testcontainer, runs Flyway against the application migration locations, asserts both tables, both unique constraints, both indexes, and the timestamp columns exist via `information_schema`, and asserts a second Flyway run applies nothing. Proof: `./mvnw -pl healthcare-integration-tests -am verify -Dit.test=DatabaseMigrationIT` passes.

### 5.3 Closure gate

- [ ] Closure gate: start the stack and the application in the dev profile; Flyway reports both migrations applied (or schema up to date on rerun); `docker compose exec app-postgres psql -U <app user> -d <app db> -c '\d inbound_messages'` and `-c '\d resource_mappings'` show the expected structure using the credentials from `.env`; `docker compose exec fhir-postgres psql -U <fhir user> -d <fhir db> -c '\dt'` shows no application tables. Record the outputs in this row; leave it unchecked if any check fails.
- [ ] Closure gate negative check: `./mvnw -pl healthcare-fhir -am dependency:tree -Dincludes=org.flywaydb*` prints nothing, proving the FHIR module cannot run schema migrations against the FHIR database. Record the empty result in this row.

## Phase 6: Patient vertical slice

### 6.1 Domain and application layer

- [ ] Domain model: `healthcare-domain/src/main/java/com/example/healthcare/domain/patient/Patient.java` (identifier system and value, given and family name, birth date, gender) with no Spring, web, or FHIR imports. Proof: `./mvnw -pl healthcare-domain test` passes and `DomainArchitectureTest` stays green.
- [ ] Port: `healthcare-domain/src/main/java/com/example/healthcare/domain/patient/PatientRepositoryPort.java` with `findByIdentifier(String system, String value)` and `save(Patient)`, matching init.md section 3, with a Javadoc note that the identifier, not the FHIR resource id, is the matching key. Proof: `./mvnw -pl healthcare-domain test` compiles and the port is implemented by the FHIR adapter in 6.2.
- [ ] Use case: `healthcare-application/src/main/java/com/example/healthcare/patient/PatientService.java` registers a patient by searching the port by identifier first and creating or updating through `save`, so re-registering the same identifier never creates a second FHIR Patient, and retrieves by resource id; constructor injection only. Proof: `PatientServiceTest` with a mocked port covers new, existing, and missing patient paths.
- [ ] DTOs: `PatientRequest` (identifier system and value, family and given name, birth date, gender) with Bean Validation constraints and `PatientResponse` (resource id plus identifier) in the same package; no FHIR types leak into the REST boundary. Proof: validation cases in `PatientControllerTest`.

### 6.2 FHIR adapter

- [ ] Mapper: `healthcare-fhir/src/main/java/com/example/healthcare/fhir/mapper/FhirPatientMapper.java` maps the domain Patient to a FHIR R4 `Patient` and back, setting `identifier`, `name`, `birthDate`, and `gender` only, using standard elements per init.md section 6. Proof: `FhirPatientMapperTest` covers both directions, a patient without a birth date, and asserts no nonstandard attributes.
- [ ] Adapter: `healthcare-fhir/src/main/java/com/example/healthcare/fhir/client/FhirPatientAdapter.java` implements `PatientRepositoryPort` over the `IGenericClient`; `findByIdentifier` issues a search on `Patient?identifier=<system>|<value>` instead of assuming the source identifier (for example PID-3) equals the FHIR resource id; `save` searches first and updates the match or creates exactly one new Patient. Proof: `PatientWorkflowIT` exercises both branches; after two registrations of the same identifier, `GET /fhir/Patient?identifier=...` returns total 1.

### 6.3 REST API

- [ ] Controller: `healthcare-application/src/main/java/com/example/healthcare/patient/PatientController.java` exposes `POST /api/v1/patients` returning 201 with a Location header containing the FHIR resource id, and `GET /api/v1/patients/{id}` returning 200 or 404; the controller delegates to `PatientService` and holds no business logic. Proof: `PatientControllerTest` with MockMvc covers 201, 400 on invalid input, and 404.
- [ ] Error handling: `GlobalExceptionHandler` returns `ProblemDetail` responses with consistent fields for Bean Validation failures and missing patients, following `docs/architecture/api-conventions.md`. Proof: assertions in `PatientControllerTest`.

### 6.4 Integration test

- [ ] Integration base: `healthcare-integration-tests/src/test/java/com/example/healthcare/support/AbstractIntegrationTest.java` starts a PostgreSQL container for the application database and a HAPI FHIR R4 JPA server container with its own PostgreSQL on a shared Testcontainers network, waits for `/fhir/metadata`, and wires `spring.datasource.*`, `spring.flyway.enabled=true`, and `healthcare.fhir.base-url` through `@DynamicPropertySource`. Proof: `PatientWorkflowIT` boots both containers with startup logs recorded.
- [ ] Workflow test: `healthcare-integration-tests/src/test/java/com/example/healthcare/PatientWorkflowIT.java` registers a synthetic patient through `POST /api/v1/patients` (201), retrieves it through `GET /api/v1/patients/{id}` (200, matching identifier), asserts the FHIR server holds exactly one matching Patient for the identifier, repeats the registration, and asserts the count is still one. Proof: `./mvnw -pl healthcare-integration-tests -am verify -Dit.test=PatientWorkflowIT` passes with output recorded.

### 6.5 Milestone demonstrations

- [ ] M1 demo: record and run `cp .env.example .env`; `./scripts/start-local.sh`; `curl -sf http://localhost:8080/fhir/metadata | grep -q CapabilityStatement`; `docker compose exec app-postgres pg_isready`; start the application with the dev profile; `curl -sf http://localhost:8081/actuator/health`. Expected: FHIR metadata reachable, both databases ready, health `UP`. Paste the outputs into this row before checking it.
- [ ] M2 demo: with the stack and application running, register with `curl -si -X POST http://localhost:8081/api/v1/patients -H 'Content-Type: application/json' -d '{"identifierSystem":"http://hospital.example.org/mrn","identifierValue":"MRN-1001","familyName":"Doe","givenName":"John","birthDate":"1990-05-15","gender":"male"}'`, expect 201 and an id; `curl -sf http://localhost:8081/api/v1/patients/<id>` returns the patient; `curl -sf 'http://localhost:8080/fhir/Patient?identifier=http://hospital.example.org/mrn|MRN-1001'` returns a Bundle with total 1; rerunning the POST keeps the total at 1. Paste the transcripts into this row.
- [ ] README milestone section: add the M1 and M2 commands to `README.md` so the demonstrations reproduce from the quickstart alone. Proof: follow the README on a clean checkout and both milestones reproduce.

### 6.6 Closure gate

- [ ] Closure gate: from the repo root on JDK 17 with Docker running, run `./mvnw clean verify`. Expected: unit tests, ArchUnit tests, `DatabaseMigrationIT`, and `PatientWorkflowIT` all pass. Record the Surefire and Failsafe summaries in this row; leave it unchecked until everything passes.

## Dependencies

- Nothing must exist before this plan: it starts from a repository that contains only `plans/demo/init.md` and the plan files. The workstation needs JDK 17, Docker with Compose v2, and a running Docker daemon for Testcontainers.
- `plans/spring-boot/02-hl7-admissions.md` receives: the five modules with enforced dependency direction, HAPI HL7v2 declared in `healthcare-hl7`, the HAPI FHIR client beans with timeouts, the local stack, the `inbound_messages` and `resource_mappings` schema, and the patient port its ADT A04 registration path routes into.
- `plans/spring-boot/03-clinical-labs.md` receives: the FHIR module structure and mapper package, the integration test base class to extend for Observation and DiagnosticReport coverage, and the FHIR client configured against the local server.
- `plans/spring-boot/04-reliability-ops.md` receives: `inbound_messages` with the status and received_at index, the structured logging baseline, `ProblemDetail` error responses, and the two-database separation its processing state machine and health checks build on.
- Canonical ledger: every active row for the foundation and local stack scope lives in this file; work owned by `plans/spring-boot/02-hl7-admissions.md`, `plans/spring-boot/03-clinical-labs.md`, and `plans/spring-boot/04-reliability-ops.md` stays in those files. Rows here stay `[ ]` until their proof succeeds, `[x]` only after the proof ran, and `[~]` with a reason and the next gate for anything deferred.

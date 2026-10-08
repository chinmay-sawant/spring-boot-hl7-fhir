# Documentation

This directory documents the Healthcare Interoperability Learning Platform. The plans under `plans/` are the source of truth and the execution ledgers; these pages describe the system those plans build and record why it is shaped this way.

## Architecture

- [System overview](architecture/system-overview.md): components, request and event flows, and the runtime topology.
- [Component diagram](architecture/component-diagram.md): the hexagonal layers and how they map to the Maven modules.
- [Sequence diagrams](architecture/sequence-diagrams.md): the ADT A01 admission flow and the HL7-to-FHIR data flow.
- [Database design](architecture/database-design.md): two-database ownership and the application tables.
- [API conventions](architecture/api-conventions.md): `/api/v1` rules and the separation from the native `/fhir` APIs.
- [Architecture decision records](architecture/architecture-decisions/README.md): the index for ADR-0001 through ADR-0005.

Two more architecture pages arrive with the build phases and are not part of the initial documentation set: `architecture/dependency-versions.md` in plan 01 phase 2.1 and `architecture/module-dependencies.md` in plan 01 phase 2.3.

## Plans

- [plans/demo/init.md](../plans/demo/init.md): the source architecture brief.
- [plans/spring-boot/01-foundation.md](../plans/spring-boot/01-foundation.md): foundation, local stack, and the patient vertical slice.
- [plans/spring-boot/02-hl7-admissions.md](../plans/spring-boot/02-hl7-admissions.md): the HL7 v2 pipeline and the ADT workflows.
- [plans/spring-boot/03-clinical-labs.md](../plans/spring-boot/03-clinical-labs.md): clinical data and laboratory results.
- [plans/spring-boot/04-reliability-ops.md](../plans/spring-boot/04-reliability-ops.md): reliability, simulator, dashboard, security, and observability.

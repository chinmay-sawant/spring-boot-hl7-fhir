# Architecture decision records

An architecture decision record captures one decision that shapes the platform, the context it was made in, and what it costs. The records below come from the architecture brief in `plans/demo/init.md` and the phase plans under `plans/spring-boot/`.

## Index

| ADR | Decision | Status |
|---|---|---|
| [ADR-0001](ADR-0001.md) | Modular monolith over microservices | Accepted |
| [ADR-0002](ADR-0002.md) | Simplified hexagonal layering | Accepted |
| [ADR-0003](ADR-0003.md) | HAPI FHIR server as the source of truth for clinical resources | Accepted |
| [ADR-0004](ADR-0004.md) | Two-database ownership | Accepted |
| [ADR-0005](ADR-0005.md) | PostgreSQL-backed processing before any message broker | Accepted |

## Format

Every record uses the headings in [template.md](template.md): Status, Context, Decision, Consequences, and Alternatives.

- Status is one of Proposed, Accepted, Superseded by ADR-NNNN, or Deprecated, with the date.
- Context states the forces at play and references the init.md section where the topic is introduced.
- Decision states what the platform does, in the present tense.
- Consequences lists what gets easier and what gets harder, including accepted risks.
- Alternatives lists the options that were rejected and the reason.

## Adding a record

Copy `template.md` to the next free number, fill every section, and add a row to the index. Superseding a decision means writing a new record and updating the Status of the old one; the old record stays in place.

-- Application database baseline, table 2 of 2 (plans/demo/init.md section 5).
-- Maps external (source) identifiers to FHIR resource ids so retransmitted
-- messages resolve to the same resource. Holds no clinical payloads.

CREATE TABLE resource_mappings (
    id UUID PRIMARY KEY,
    source_system VARCHAR(100) NOT NULL,
    source_identifier_system VARCHAR(255) NOT NULL,
    source_identifier_value VARCHAR(255) NOT NULL,
    fhir_resource_type VARCHAR(100) NOT NULL,
    fhir_resource_id VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_source_resource
        UNIQUE (
            source_system,
            source_identifier_system,
            source_identifier_value,
            fhir_resource_type
        )
);

-- Reverse lookup: given a FHIR resource, find the source identifier it came
-- from (for example when reconciling a message against an existing resource).
CREATE INDEX ix_resource_mappings_fhir
    ON resource_mappings (fhir_resource_type, fhir_resource_id);

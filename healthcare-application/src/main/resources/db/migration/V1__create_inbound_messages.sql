-- Application database baseline, table 1 of 2 (plans/demo/init.md section 5).
-- Stores HL7 message metadata and processing state only; clinical resources
-- live in the HAPI FHIR server database, never here.

CREATE TABLE inbound_messages (
    id UUID PRIMARY KEY,
    source_system VARCHAR(100) NOT NULL,
    message_control_id VARCHAR(150) NOT NULL,
    message_type VARCHAR(50) NOT NULL,
    processing_status VARCHAR(30) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    error_code VARCHAR(100),
    error_summary TEXT,

    CONSTRAINT uq_source_message
        UNIQUE (source_system, message_control_id)
);

-- Duplicate detection/reprocessing looks messages up by state over time, and
-- the reliability work (plans/spring-boot/04-reliability-ops.md) sweeps this
-- pair for retry and quarantine handling.
CREATE INDEX ix_inbound_messages_status_received
    ON inbound_messages (processing_status, received_at);

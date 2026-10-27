CREATE TABLE outcome_events (
    id BIGSERIAL PRIMARY KEY,
    job_instance_id BIGINT NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    source_event_id VARCHAR(255) NOT NULL,
    persisted_records BIGINT,
    match_id TEXT,
    period TEXT,
    category TEXT,
    metric TEXT,
    reason_detail TEXT,
    input_source TEXT NOT NULL,
    input_partition INT NOT NULL,
    input_offset BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    sent BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX outcome_events_pending ON outcome_events (job_instance_id, id) WHERE NOT sent;

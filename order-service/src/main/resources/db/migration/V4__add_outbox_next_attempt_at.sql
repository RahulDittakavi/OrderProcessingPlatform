ALTER TABLE outbox_events
    ADD COLUMN next_attempt_at TIMESTAMPTZ;

CREATE INDEX idx_outbox_events_status_next_attempt_created_at
    ON outbox_events (status, next_attempt_at, created_at);

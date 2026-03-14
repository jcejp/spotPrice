CREATE TABLE sync_log (
    id           BIGSERIAL    PRIMARY KEY,
    sync_time    TIMESTAMPTZ  NOT NULL,
    payload_hash VARCHAR(64)  NOT NULL
);

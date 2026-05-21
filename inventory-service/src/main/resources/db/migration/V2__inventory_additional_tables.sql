CREATE TABLE inventory.ipa_config (
    eid VARCHAR(32) PRIMARY KEY REFERENCES inventory.devices(eid) ON DELETE CASCADE,
    ipa_mode VARCHAR(10),
    poll_interval_seconds INTEGER DEFAULT 14400,
    configured_eim_url VARCHAR(500),
    last_poll_at TIMESTAMP,
    last_poll_result VARCHAR(20)
);

CREATE TABLE inventory.euicc_info (
    eid VARCHAR(32) PRIMARY KEY REFERENCES inventory.devices(eid) ON DELETE CASCADE,
    euicc_form_factor VARCHAR(10),
    profile_slots_available INTEGER,
    sgp_version VARCHAR(30),
    euicc_category VARCHAR(20)
);

CREATE TABLE inventory.device_tags (
    id BIGSERIAL PRIMARY KEY,
    eid VARCHAR(32) NOT NULL REFERENCES inventory.devices(eid) ON DELETE CASCADE,
    tag_key VARCHAR(100) NOT NULL,
    tag_value VARCHAR(200) NOT NULL,
    UNIQUE (eid, tag_key)
);

CREATE TABLE inventory.ingest_jobs (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    total_records BIGINT DEFAULT 0,
    processed_records BIGINT DEFAULT 0,
    failed_records BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP
);

CREATE TABLE inventory.ingest_rows (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES inventory.ingest_jobs(id) ON DELETE CASCADE,
    row_number BIGINT NOT NULL,
    eid VARCHAR(32),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

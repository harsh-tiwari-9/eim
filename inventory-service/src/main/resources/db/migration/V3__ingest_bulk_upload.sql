ALTER TABLE inventory.ingest_jobs
    ADD COLUMN uploaded_by VARCHAR(100),
    ADD COLUMN input_file_path VARCHAR(500),
    ADD COLUMN output_file_path VARCHAR(500),
    ADD COLUMN remarks TEXT,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

ALTER TABLE inventory.ingest_jobs
    ALTER COLUMN status SET DEFAULT 'UPLOADED';

ALTER TABLE inventory.ingest_rows
    ADD COLUMN payload_json JSONB,
    ADD COLUMN remarks TEXT,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();
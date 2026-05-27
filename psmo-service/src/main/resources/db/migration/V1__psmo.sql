CREATE SCHEMA IF NOT EXISTS psmo;

CREATE TABLE psmo.operations (
     id              BIGSERIAL PRIMARY KEY,
     eid             VARCHAR(32) NOT NULL,
     type            VARCHAR(30) NOT NULL,
     target_iccid    VARCHAR(20),
     params          JSONB,
     status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
     requested_by    VARCHAR(100) NOT NULL,
     result_payload  JSONB,
     created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
     updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
     signed_at       TIMESTAMP,
     sent_at         TIMESTAMP,
     completed_at    TIMESTAMP
);

CREATE INDEX idx_operations_eid_status ON psmo.operations (eid, status);
CREATE INDEX idx_operations_created    ON psmo.operations (created_at DESC);

CREATE TABLE psmo.operation_logs (
     id            BIGSERIAL PRIMARY KEY,
     operation_id  BIGINT NOT NULL REFERENCES psmo.operations(id) ON DELETE CASCADE,
     event_type    VARCHAR(40) NOT NULL,
     actor         VARCHAR(100),
     details       JSONB,
     ts            TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_operation_logs_op ON psmo.operation_logs (operation_id, ts);


CREATE TABLE psmo.signed_packages (
      operation_id     BIGINT PRIMARY KEY REFERENCES psmo.operations(id) ON DELETE CASCADE,
      package_bytes    BYTEA NOT NULL,
      package_format   VARCHAR(10) NOT NULL,
      signature_alg    VARCHAR(20) NOT NULL,
      signature        BYTEA,
      created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE psmo.device_pending (
         eid           VARCHAR(32) PRIMARY KEY,
         operation_id  BIGINT NOT NULL REFERENCES psmo.operations(id) ON DELETE CASCADE,
         queued_at     TIMESTAMP NOT NULL DEFAULT NOW()
);




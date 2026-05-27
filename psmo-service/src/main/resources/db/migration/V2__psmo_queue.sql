ALTER TABLE psmo.device_pending DROP CONSTRAINT device_pending_pkey;

ALTER TABLE psmo.device_pending ADD COLUMN id BIGSERIAL PRIMARY KEY;

ALTER TABLE psmo.device_pending
    ADD CONSTRAINT device_pending_op_uk UNIQUE (operation_id);

CREATE INDEX idx_device_pending_eid_queued
    ON psmo.device_pending (eid, queued_at);
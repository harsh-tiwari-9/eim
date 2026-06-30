-- Per-eUICC replay-protection counter for eUICC Packages issued by this eIM (SGP.32 §2.11.1).
-- The eIM increments counter_value by 1 for each package sent to the target eUICC; the eUICC
-- stores the highest received value per Associated eIM.
CREATE TABLE psmo.eim_package_counter (
    eid           VARCHAR(32) PRIMARY KEY,
    eim_id        VARCHAR(128),
    counter_value BIGINT NOT NULL DEFAULT 0
);
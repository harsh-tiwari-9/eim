-- Enrich device_profiles so it can be the single source of truth for the "Profiles Information"
-- view. Additive only (new nullable columns) — the existing columns and the registration flow are
-- untouched. Populated by psmo-service from AUDIT results and kept current by enable/disable/delete.
ALTER TABLE inventory.device_profiles
    ADD COLUMN profile_name           VARCHAR(64),
    ADD COLUMN service_provider_name  VARCHAR(64),
    ADD COLUMN profile_class_name     VARCHAR(20),   -- human name from AUDIT: test|provisioning|operational
    ADD COLUMN fallback_allowed       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN label                  VARCHAR(64),   -- profileNickname
    ADD COLUMN updated_at             TIMESTAMP;

CREATE SCHEMA IF NOT EXISTS inventory;

CREATE TABLE inventory.devices (
    eid VARCHAR(32) PRIMARY KEY,
    owner_id VARCHAR(100) NOT NULL,
    auto_enable BOOLEAN NOT NULL DEFAULT FALSE,
    auto_delete BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
    registered_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE inventory.device_profiles (
    id BIGSERIAL PRIMARY KEY,
    eid VARCHAR(32) NOT NULL REFERENCES inventory.devices(eid) ON DELETE CASCADE,
    iccid VARCHAR(20) NOT NULL,
    state VARCHAR(20) NOT NULL,
    profile_class CHAR(1),
    mno_id VARCHAR(50),
    is_fallback BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (eid, iccid)
);

CREATE TABLE inventory.ipa_capabilities (
    eid VARCHAR(32) PRIMARY KEY REFERENCES inventory.devices(eid) ON DELETE CASCADE,
    direct_rsp_server_communication BOOLEAN NOT NULL DEFAULT FALSE,
    indirect_rsp_server_communication BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE inventory.euicc_certs (
    eid VARCHAR(32) PRIMARY KEY REFERENCES inventory.devices(eid) ON DELETE CASCADE,
    euicc_cert_base64 TEXT NOT NULL,
    eum_cert_base64 TEXT NOT NULL,
    euicc_public_key_hex VARCHAR(300),
    euicc_subject VARCHAR(300),
    eum_subject VARCHAR(300),
    ci_reference VARCHAR(100),
    cert_valid_from TIMESTAMP,
    cert_valid_to TIMESTAMP,
    chain_valid BOOLEAN NOT NULL DEFAULT FALSE
);

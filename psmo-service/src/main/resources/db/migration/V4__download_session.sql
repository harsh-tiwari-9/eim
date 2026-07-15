-- Tracks an in-flight indirect profile-download RSP session (SGP.32 §6.3.2). Steps 2-4 of the
-- handshake carry only the SM-DP+-generated transactionId (not the SM-DP+ address), so the eIM
-- stores the address + correlation here at InitiateAuthentication and looks it up thereafter.
CREATE TABLE psmo.download_session (
    transaction_id  VARCHAR(32) PRIMARY KEY,   -- SM-DP+ TransactionId, hex (OCTET STRING 1..16)
    eid             VARCHAR(32) NOT NULL,
    smdp_address    VARCHAR(255) NOT NULL,
    operation_id    BIGINT,                    -- echoed eimTransactionId -> psmo.operations.id
    status          VARCHAR(20) NOT NULL,      -- INITIATED, AUTHENTICATED, BOUND, COMPLETED, FAILED, CANCELLED
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_download_session_eid ON psmo.download_session (eid);
CREATE INDEX idx_download_session_op  ON psmo.download_session (operation_id);
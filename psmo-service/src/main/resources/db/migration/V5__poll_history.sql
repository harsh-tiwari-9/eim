-- Append-only history of every ESipa retrieval (getEimPackageRequest) a device makes. One row per
-- poll — recorded whether or not a package was served — so we can derive fleet reachability
-- ("SIMs polling in the last 24h" = COUNT(DISTINCT eid)), per-device poll timelines, and serve rate.
-- Populated by EsipaService.getNextPackage; intended to be reused by other APIs.
CREATE TABLE psmo.poll_history (
    id           BIGSERIAL PRIMARY KEY,
    eid          VARCHAR(32) NOT NULL,
    polled_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    had_package  BOOLEAN     NOT NULL DEFAULT FALSE,  -- true if a signed package was returned on this poll
    operation_id BIGINT                               -- the op served on this poll, if any -> psmo.operations.id
);

-- COUNT(DISTINCT eid) WHERE polled_at >= now()-24h (dashboard "SIMs polling") + time-range scans.
CREATE INDEX idx_poll_history_polled_at ON psmo.poll_history (polled_at);
-- Per-device newest-first timeline (future "device poll history" API).
CREATE INDEX idx_poll_history_eid_ts    ON psmo.poll_history (eid, polled_at DESC);
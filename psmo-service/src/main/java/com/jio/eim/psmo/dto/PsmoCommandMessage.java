package com.jio.eim.psmo.dto;

import java.time.Instant;

public record PsmoCommandMessage(
        long operationId,
        String eid,
        String type,
        String targetIccid,
        String activationCode,
        String requestedBy,
        Instant ts
) {}
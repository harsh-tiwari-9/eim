package com.jio.eim.psmo.dto;

import java.time.Instant;
import java.util.Map;

public record PsmoCommandMessage(
        long operationId,
        String eid,
        String type,
        String targetIccid,
        String requestedBy,
        Instant ts,
        Map<String, Object> params
) {}
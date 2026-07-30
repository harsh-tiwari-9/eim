package com.jio.eim.psmo.dto;

import java.time.Instant;

/**
 * psmo-owned device-detail fields for the device-info panel: the eUICC data from the most recent
 * successful {@code EUICC_DATA} ({@code IpaEuiccData}) operation, plus the last-audit and last-poll
 * timestamps. Inventory-owned fields (registration date, EUM name/id, IPA mode) come from a separate
 * inventory-service endpoint. All eUICC fields are null until an {@code EUICC_DATA} op has succeeded.
 */
public record DeviceEuiccInfoResponse(
        String eid,
        String defaultSmdpAddress,
        String rootSmdsAddress,
        String profileVersion,
        String svn,
        String euiccFirmwareVer,
        String tac,
        Instant euiccDataAt,   // when the eUICC data above was last read
        Instant lastAuditAt,   // last successful AUDIT
        Instant lastPolledAt   // last ESipa poll
) {}
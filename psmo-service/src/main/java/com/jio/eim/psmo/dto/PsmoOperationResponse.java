package com.jio.eim.psmo.dto;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PsmoOperationResponse {

    private Long operationId;
    private String eid;
    private String type;
    private String targetIccid;
    private String status;
    private String requestedBy;
    private String params;
    private String resultPayload;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant signedAt;
    private Instant sentAt;
    private Instant completedAt;
}
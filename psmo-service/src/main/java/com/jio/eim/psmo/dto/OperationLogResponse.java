package com.jio.eim.psmo.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogResponse {

    private Long id;
    private Long operationId;
    private String eventType;
    private String actor;
    private String details;
    private Instant ts;

    // Operation context joined from psmo.operations
    private String eid;
    private String operationType;
    private String targetIccid;
    private String operationStatus;
}
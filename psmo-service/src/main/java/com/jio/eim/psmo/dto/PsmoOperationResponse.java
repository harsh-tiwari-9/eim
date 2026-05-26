package com.jio.eim.psmo.dto;

import java.time.Instant;

public class PsmoOperationResponse {

    private Long operationId;
    private String eid;
    private String type;
    private String targetIccid;
    private String status;
    private String requestedBy;
    private String resultPayload;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant signedAt;
    private Instant sentAt;
    private Instant completedAt;

    public Long getOperationId() { return operationId; }
    public void setOperationId(Long operationId) { this.operationId = operationId; }

    public String getEid() { return eid; }
    public void setEid(String eid) { this.eid = eid; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTargetIccid() { return targetIccid; }
    public void setTargetIccid(String targetIccid) { this.targetIccid = targetIccid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public String getResultPayload() { return resultPayload; }
    public void setResultPayload(String resultPayload) { this.resultPayload = resultPayload; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getSignedAt() { return signedAt; }
    public void setSignedAt(Instant signedAt) { this.signedAt = signedAt; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
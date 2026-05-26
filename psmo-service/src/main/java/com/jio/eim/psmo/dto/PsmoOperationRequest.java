package com.jio.eim.psmo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class PsmoOperationRequest {

    @NotBlank
    @Pattern(regexp = "[0-9A-F]{20,32}")
    private String eid;

    @NotBlank
    @Pattern(regexp = "AUDIT|ENABLE|DISABLE|DELETE|DOWNLOAD")
    private String type;

    private String targetIccid;

    public String getEid() { return eid; }
    public void setEid(String eid) { this.eid = eid; }

    public String getType() { return type; }
    public void setType(String type) { this.type=type; }

    public String getTargetIccid() { return targetIccid; }
    public void setTargetIccid(String targetIccid) { this.targetIccid = targetIccid; }
}
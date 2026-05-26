package com.jio.eim.psmo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PsmoOperationRequest {

    @NotBlank
    @Pattern(regexp = "[0-9A-F]{20,32}")
    private String eid;

    @NotBlank
    @Pattern(regexp = "AUDIT|ENABLE|DISABLE|DELETE|DOWNLOAD")
    private String type;

    private String targetIccid;
}
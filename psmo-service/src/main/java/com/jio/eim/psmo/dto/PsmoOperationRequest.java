package com.jio.eim.psmo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PsmoOperationRequest {

    @NotBlank
    @Pattern(regexp = "[0-9A-F]{20,32}")
    private String eid;

    @NotBlank
    @Pattern(regexp = "AUDIT|ENABLE|DISABLE|DELETE|DOWNLOAD|UPDATE_POLLING_INTERVAL")
    private String type;

    private String targetIccid;

    /**
     * Operation parameters persisted to operations.params (jsonb). For
     * UPDATE_POLLING_INTERVAL this carries {@code pollingIntervalSeconds}.
     */
    private Map<String, Object> params;
}
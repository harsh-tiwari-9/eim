package com.jio.eim.psmo.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Request body for the operation-status refresh endpoint: the operation ids currently on screen. */
@Getter
@Setter
public class PsmoOperationRefreshRequest {

    /** Operation ids to fetch fresh status for (typically the non-terminal rows shown in the UI). */
    @NotEmpty
    @Size(max = 200, message = "at most 200 operationIds per refresh")
    private List<Long> operationIds;
}
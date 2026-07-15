package com.jio.eim.psmo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    /** Target profile ICCID (decimal) for ENABLE/DISABLE/DELETE. */
    private String targetIccid;

    /**
     * For DOWNLOAD: the SGP.22 activation code (e.g. {@code 1$smdp.example.com$MATCHING-ID}); a
     * leading {@code LPA:} scheme prefix is stripped. When omitted, the download trigger tells the
     * eUICC to contact its default SM-DP+ instead ({@code contactDefaultSmdp}).
     */
    @Size(max = 255)
    private String activationCode;
}
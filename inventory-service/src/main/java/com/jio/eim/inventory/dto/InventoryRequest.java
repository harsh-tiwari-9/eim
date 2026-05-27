package com.jio.eim.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InventoryRequest {

    @NotBlank
    @Pattern(regexp = "\\d{32}", message = "eid must be exactly 32 digits")
    private String eid;

    @NotBlank
    private String ownerId;

    @Valid
    private List<ProfileDto> profiles;

    private String autoEnable;

    private String autoDelete;

    @Valid
    private IpaCapabilitiesDto ipaCapabilities;

    @NotEmpty
    @Valid
    private List<EuiccEumCertDto> euiccEumCerts;

    @Getter
    @Setter
    public static class ProfileDto {
        private String iccid;
        private String state;
        private String profileClass;
    }

    @Getter
    @Setter
    public static class IpaCapabilitiesDto {
        private boolean directRspServerCommunication;
        private boolean indirectRspServerCommunication;
    }

    @Getter
    @Setter
    public static class EuiccEumCertDto {
        @NotBlank
        private String euiccCertAsBase64;

        @NotBlank
        private String eumCertAsBase64;
    }
}
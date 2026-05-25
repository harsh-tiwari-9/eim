package com.jio.eim.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;

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

    public String getEid() {
        return eid;
    }

    public void setEid(String eid) {
        this.eid = eid;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public List<ProfileDto> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<ProfileDto> profiles) {
        this.profiles = profiles;
    }

    public String getAutoEnable() {
        return autoEnable;
    }

    public void setAutoEnable(String autoEnable) {
        this.autoEnable = autoEnable;
    }

    public String getAutoDelete() {
        return autoDelete;
    }

    public void setAutoDelete(String autoDelete) {
        this.autoDelete = autoDelete;
    }

    public IpaCapabilitiesDto getIpaCapabilities() {
        return ipaCapabilities;
    }

    public void setIpaCapabilities(IpaCapabilitiesDto ipaCapabilities) {
        this.ipaCapabilities = ipaCapabilities;
    }

    public List<EuiccEumCertDto> getEuiccEumCerts() {
        return euiccEumCerts;
    }

    public void setEuiccEumCerts(List<EuiccEumCertDto> euiccEumCerts) {
        this.euiccEumCerts = euiccEumCerts;
    }

    public static class ProfileDto {
        private String iccid;
        private String state;
        private String profileClass;

        public String getIccid() {
            return iccid;
        }

        public void setIccid(String iccid) {
            this.iccid = iccid;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getProfileClass() {
            return profileClass;
        }

        public void setProfileClass(String profileClass) {
            this.profileClass = profileClass;
        }
    }

    public static class IpaCapabilitiesDto {
        private boolean directRspServerCommunication;
        private boolean indirectRspServerCommunication;

        public boolean isDirectRspServerCommunication() {
            return directRspServerCommunication;
        }

        public void setDirectRspServerCommunication(boolean directRspServerCommunication) {
            this.directRspServerCommunication = directRspServerCommunication;
        }

        public boolean isIndirectRspServerCommunication() {
            return indirectRspServerCommunication;
        }

        public void setIndirectRspServerCommunication(boolean indirectRspServerCommunication) {
            this.indirectRspServerCommunication = indirectRspServerCommunication;
        }
    }

    public static class EuiccEumCertDto {
        @NotBlank
        private String euiccCertAsBase64;

        @NotBlank
        private String eumCertAsBase64;

        public String getEuiccCertAsBase64() {
            return euiccCertAsBase64;
        }

        public void setEuiccCertAsBase64(String euiccCertAsBase64) {
            this.euiccCertAsBase64 = euiccCertAsBase64;
        }

        public String getEumCertAsBase64() {
            return eumCertAsBase64;
        }

        public void setEumCertAsBase64(String eumCertAsBase64) {
            this.eumCertAsBase64 = eumCertAsBase64;
        }
    }
}

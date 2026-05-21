package com.jio.eim.inventory.dto;

import java.time.Instant;
import java.util.List;

public class InventoryResponse {

    private String eid;
    private String ownerId;
    private String status;
    private String autoEnable;
    private String autoDelete;
    private List<ProfileView> profiles;
    private IpaCapabilitiesView ipaCapabilities;
    private CertSummary certInfo;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public List<ProfileView> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<ProfileView> profiles) {
        this.profiles = profiles;
    }

    public IpaCapabilitiesView getIpaCapabilities() {
        return ipaCapabilities;
    }

    public void setIpaCapabilities(IpaCapabilitiesView ipaCapabilities) {
        this.ipaCapabilities = ipaCapabilities;
    }

    public CertSummary getCertInfo() {
        return certInfo;
    }

    public void setCertInfo(CertSummary certInfo) {
        this.certInfo = certInfo;
    }

    public static class ProfileView {
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

    public static class IpaCapabilitiesView {
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

    public static class CertSummary {
        private boolean chainValid;
        private String euiccSubject;
        private String eumSubject;
        private String euiccPublicKeyHex;
        private Instant certValidFrom;
        private Instant certValidTo;

        public boolean isChainValid() {
            return chainValid;
        }

        public void setChainValid(boolean chainValid) {
            this.chainValid = chainValid;
        }

        public String getEuiccSubject() {
            return euiccSubject;
        }

        public void setEuiccSubject(String euiccSubject) {
            this.euiccSubject = euiccSubject;
        }

        public String getEumSubject() {
            return eumSubject;
        }

        public void setEumSubject(String eumSubject) {
            this.eumSubject = eumSubject;
        }

        public String getEuiccPublicKeyHex() {
            return euiccPublicKeyHex;
        }

        public void setEuiccPublicKeyHex(String euiccPublicKeyHex) {
            this.euiccPublicKeyHex = euiccPublicKeyHex;
        }

        public Instant getCertValidFrom() {
            return certValidFrom;
        }

        public void setCertValidFrom(Instant certValidFrom) {
            this.certValidFrom = certValidFrom;
        }

        public Instant getCertValidTo() {
            return certValidTo;
        }

        public void setCertValidTo(Instant certValidTo) {
            this.certValidTo = certValidTo;
        }
    }
}

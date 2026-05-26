package com.jio.eim.inventory.dto;

import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InventoryResponse {

    private String eid;
    private String ownerId;
    private String status;
    private String autoEnable;
    private String autoDelete;
    private List<ProfileView> profiles;
    private IpaCapabilitiesView ipaCapabilities;
    private CertSummary certInfo;

    @Getter
    @Setter
    public static class ProfileView {
        private String iccid;
        private String state;
        private String profileClass;
    }

    @Getter
    @Setter
    public static class IpaCapabilitiesView {
        private boolean directRspServerCommunication;
        private boolean indirectRspServerCommunication;
    }

    @Getter
    @Setter
    public static class CertSummary {
        private boolean chainValid;
        private String euiccSubject;
        private String eumSubject;
        private String euiccPublicKeyHex;
        private Instant certValidFrom;
        private Instant certValidTo;
    }
}
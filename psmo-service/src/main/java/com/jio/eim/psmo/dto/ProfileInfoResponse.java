package com.jio.eim.psmo.dto;

import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * On-card profile information for the UI "Profiles Information" view. Sourced from the most recent
 * successful AUDIT for the device, so it reflects the eUICC's actual state as of {@code auditedAt}.
 */
@Getter
@Setter
public class ProfileInfoResponse {

    /** EID this profile list belongs to. */
    private String eid;

    /** When the AUDIT this snapshot came from completed (null if never audited). */
    private Instant auditedAt;

    /** Operation id of the AUDIT this snapshot came from. */
    private Long auditOperationId;

    private List<Profile> profiles;

    @Getter
    @Setter
    public static class Profile {
        private String iccid;                 // decimal ICCID
        private String state;                 // enabled | disabled
        private boolean fallbackAttribute;    // is this the Fallback Profile
        private boolean fallbackAllowed;      // is Fallback allowed for this profile
        private String profileClass;          // test | provisioning | operational
        private String label;                 // profileNickname
        private String profileName;
        private String serviceProviderName;
    }
}
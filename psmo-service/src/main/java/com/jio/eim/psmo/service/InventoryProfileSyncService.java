package com.jio.eim.psmo.service;

import com.jio.eim.psmo.entity.InventoryDeviceProfile;
import com.jio.eim.psmo.repository.InventoryDeviceProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles {@code inventory.device_profiles} with the eUICC's actual on-card profiles from a
 * successful AUDIT. Without this, {@code device_profiles} is only a registration-time snapshot and
 * drifts from reality as profiles are downloaded/enabled/deleted on the card. AUDIT returns the
 * full on-card list, so this fully replaces the stored profiles for the EID (delete + re-insert).
 *
 * <p>Runs in its own transaction ({@code REQUIRES_NEW}) so a sync failure never rolls back the
 * operation status update / eUICC acknowledgement in the caller.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryProfileSyncService {

    private final InventoryDeviceProfileRepository profileRepository;

    /**
     * Replaces the stored profiles for {@code eid} with those in the decoded AUDIT result details.
     * Only acts when the details actually contain a {@code listProfileInfo} profile list — a result
     * that carries no profile list (error branch, non-AUDIT) is left untouched so nothing is wiped.
     *
     * @return number of profiles written, or -1 if there was no profile list to sync
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int syncFromAuditDetails(String eid, Map<String, Object> details) {
        List<Map<String, Object>> profiles = extractProfileList(details);
        if (profiles == null) {
            log.debug("AUDIT for {} carried no listProfileInfo — leaving device_profiles untouched", eid);
            return -1;
        }

        profileRepository.deleteByEid(eid);
        profileRepository.flush();

        int written = 0;
        for (Map<String, Object> p : profiles) {
            String iccid = asString(p.get("iccid"));
            String state = asString(p.get("state"));
            if (iccid == null || state == null) {
                log.warn("Skipping AUDIT profile for {} missing iccid/state: {}", eid, p);
                continue;
            }
            InventoryDeviceProfile entity = new InventoryDeviceProfile();
            entity.setEid(eid);
            entity.setIccid(iccid);
            entity.setState(state);
            entity.setFallback(asBool(p.get("fallbackAttribute")));
            entity.setFallbackAllowed(asBool(p.get("fallbackAllowed")));
            entity.setProfileClassName(asString(p.get("profileClass")));
            entity.setLabel(asString(p.get("label")));
            entity.setProfileName(asString(p.get("profileName")));
            entity.setServiceProviderName(asString(p.get("serviceProviderName")));
            entity.setUpdatedAt(Instant.now());
            profileRepository.save(entity);
            written++;
        }
        log.info("Synced {} profile(s) into inventory.device_profiles for {} from AUDIT", written, eid);
        return written;
    }

    /**
     * Applies an enable locally: {@code iccid} becomes the sole enabled profile for {@code eid} (all
     * others disabled). Covers ENABLE (target) and DISABLE-by-enable (the enableIccid). No AUDIT
     * needed — the state change is fully known from the operation. Runs in its own transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setEnabled(String eid, String enabledIccid) {
        List<InventoryDeviceProfile> profiles = profileRepository.findByEid(eid);
        Instant now = Instant.now();
        boolean matched = false;
        for (InventoryDeviceProfile p : profiles) {
            String newState = p.getIccid().equals(enabledIccid) ? "enabled" : "disabled";
            if (!newState.equals(p.getState())) {
                p.setState(newState);
                p.setUpdatedAt(now);
                profileRepository.save(p);
            }
            matched |= p.getIccid().equals(enabledIccid);
        }
        if (!matched) {
            log.warn("setEnabled: iccid {} not in device_profiles for {} — run an AUDIT to populate", enabledIccid, eid);
        }
    }

    /** Removes a profile locally after a successful DELETE. Runs in its own transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteProfile(String eid, String iccid) {
        profileRepository.deleteByEidAndIccid(eid, iccid);
    }

    /** Pulls the profile list out of the decoder's details: results[].type=listProfileInfo -> profiles[]. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractProfileList(Map<String, Object> details) {
        if (details == null) {
            return null;
        }
        Object results = details.get("results");
        if (!(results instanceof List<?> resultList)) {
            return null;
        }
        for (Object entry : resultList) {
            if (entry instanceof Map<?, ?> map
                    && "listProfileInfo".equals(map.get("type"))
                    && map.get("profiles") instanceof List<?> profiles) {
                return (List<Map<String, Object>>) profiles;
            }
        }
        return null;
    }

    private static String asString(Object o) {
        return (o == null) ? null : o.toString();
    }

    private static boolean asBool(Object o) {
        return (o instanceof Boolean b) ? b : Boolean.parseBoolean(String.valueOf(o));
    }
}
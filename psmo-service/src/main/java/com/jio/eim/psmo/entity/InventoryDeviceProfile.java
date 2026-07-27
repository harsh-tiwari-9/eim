package com.jio.eim.psmo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Read/write view of {@code inventory.device_profiles} from psmo-service, used to reconcile the
 * eIM's stored profile list with the eUICC's actual on-card profiles after a successful AUDIT.
 * psmo already reads {@code inventory.devices} directly (see {@link InventoryDeviceLookup}); this
 * mirrors that pattern for the profiles table.
 */
@Entity
@Table(schema = "inventory", name = "device_profiles")
@Getter
@Setter
public class InventoryDeviceProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String eid;

    @Column(nullable = false, length = 20)
    private String iccid;

    @Column(nullable = false, length = 20)
    private String state;

    // Note: the legacy profile_class (CHAR(1)) and mno_id columns are intentionally NOT mapped here —
    // mapping the CHAR(1) column trips Hibernate schema validation (bpchar vs varchar), and they are
    // owned by inventory-service's registration flow. The profile-info fields below (added in V4) are
    // what psmo maintains: full-replaced on AUDIT, and state kept current on enable/disable/delete.

    @Column(name = "is_fallback", nullable = false)
    private boolean fallback;                 // Fallback Attribute

    @Column(name = "fallback_allowed", nullable = false)
    private boolean fallbackAllowed;

    @Column(name = "profile_class_name", length = 20)
    private String profileClassName;          // test | provisioning | operational

    @Column(length = 64)
    private String label;                     // profileNickname

    @Column(name = "profile_name", length = 64)
    private String profileName;

    @Column(name = "service_provider_name", length = 64)
    private String serviceProviderName;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
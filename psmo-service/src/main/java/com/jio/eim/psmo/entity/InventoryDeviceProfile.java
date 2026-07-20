package com.jio.eim.psmo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    @Column(name = "profile_class", length = 1)
    private String profileClass;

    @Column(name = "mno_id", length = 50)
    private String mnoId;

    @Column(name = "is_fallback", nullable = false)
    private boolean fallback;
}
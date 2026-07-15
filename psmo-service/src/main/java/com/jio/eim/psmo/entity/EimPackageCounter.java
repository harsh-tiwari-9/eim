package com.jio.eim.psmo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Per-eUICC replay-protection counter for eUICC Packages issued by this eIM (SGP.32 §2.11.1).
 * Incremented by 1 for each package sent to the target eUICC.
 */
@Entity
@Table(schema = "psmo", name = "eim_package_counter")
@Getter
@Setter
public class EimPackageCounter {

    @Id
    @Column(name = "eid", length = 32)
    private String eid;

    @Column(name = "eim_id", length = 128)
    private String eimId;

    @Column(name = "counter_value", nullable = false)
    private long counterValue;
}
package com.jio.eim.psmo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * One append-only record per ESipa poll (getEimPackageRequest) a device makes — see
 * {@code psmo.poll_history}. Written on every poll, including the "no package available" ones, so it
 * doubles as the fleet-reachability signal and a per-device poll timeline for other APIs.
 */
@Entity
@Table(schema = "psmo", name = "poll_history")
@Getter
@Setter
public class PollHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String eid;

    @Column(name = "polled_at", nullable = false)
    private Instant polledAt;

    /** True when this poll returned a signed package (as opposed to noEimPackageAvailable). */
    @Column(name = "had_package", nullable = false)
    private boolean hadPackage;

    /** The operation served on this poll, if any. Null for empty polls. */
    @Column(name = "operation_id")
    private Long operationId;

    @PrePersist
    void onCreate() {
        if (polledAt == null) {
            polledAt = Instant.now();
        }
    }
}
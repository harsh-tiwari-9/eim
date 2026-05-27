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

@Entity
@Table(schema = "psmo", name = "device_pending")
@Getter
@Setter
public class DevicePending {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String eid;

    @Column(name = "operation_id", nullable = false)
    private Long operationId;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @PrePersist
    void onCreate() {
        if (queuedAt == null) queuedAt = Instant.now();
    }
}
package com.jio.eim.psmo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * An in-flight indirect profile-download RSP session, keyed by the SM-DP+ {@code transactionId}.
 * Created at InitiateAuthentication; looked up on the AuthenticateClient / GetBoundProfilePackage /
 * HandleNotification steps (which carry only the transactionId, not the SM-DP+ address).
 */
@Entity
@Table(schema = "psmo", name = "download_session")
@Getter
@Setter
public class DownloadSession {

    @Id
    @Column(name = "transaction_id", length = 32)
    private String transactionId;

    @Column(nullable = false, length = 32)
    private String eid;

    @Column(name = "smdp_address", nullable = false, length = 255)
    private String smdpAddress;

    @Column(name = "operation_id")
    private Long operationId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
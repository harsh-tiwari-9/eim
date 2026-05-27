package com.jio.eim.psmo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(schema = "psmo", name = "signed_packages")
@Getter
@Setter
public class SignedPackage {

    @Id
    @Column(name = "operation_id")
    private Long operationId;

    @Column(name = "package_bytes", nullable = false)
    private byte[] packageBytes;

    @Column(name = "package_format", nullable = false, length = 10)
    private String packageFormat;

    @Column(name = "signature_alg", nullable = false, length = 20)
    private String signatureAlg;

    private byte[] signature;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
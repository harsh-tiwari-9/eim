package com.jio.eim.psmo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(schema = "psmo", name="signed_packages")
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

    public Long getOperationId() { return operationId; }
    public void setOperationId(Long operationId) { this.operationId = operationId; }

    public byte[] getPackageBytes() { return packageBytes; }
    public void setPackageBytes(byte[] packageBytes) { this.packageBytes = packageBytes; }

    public String getPackageFormat() { return packageFormat; }
    public void setPackageFormat(String packageFormat) { this.packageFormat = packageFormat; }

    public String getSignatureAlg() { return signatureAlg; }
    public void setSignatureAlg(String signatureAlg) { this.signatureAlg = signatureAlg; }

    public byte[] getSignature() { return signature; }
    public void setSignature(byte[] signature) { this.signature = signature; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
package com.jio.eim.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(schema = "inventory", name = "euicc_certs")
@Getter
@Setter
public class EuiccCert {

    @Id
    @Column(length = 32)
    private String eid;

    @Column(name = "euicc_cert_base64", nullable = false, columnDefinition = "TEXT")
    private String euiccCertBase64;

    @Column(name = "eum_cert_base64", nullable = false, columnDefinition = "TEXT")
    private String eumCertBase64;

    @Column(name = "euicc_public_key_hex", length = 300)
    private String euiccPublicKeyHex;

    @Column(name = "euicc_subject", length = 300)
    private String euiccSubject;

    @Column(name = "eum_subject", length = 300)
    private String eumSubject;

    @Column(name = "ci_reference", length = 100)
    private String ciReference;

    @Column(name = "cert_valid_from")
    private Instant certValidFrom;

    @Column(name = "cert_valid_to")
    private Instant certValidTo;

    @Column(name = "chain_valid", nullable = false)
    private boolean chainValid;
}
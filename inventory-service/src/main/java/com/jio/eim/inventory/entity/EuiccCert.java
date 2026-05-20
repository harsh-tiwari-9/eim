package com.jio.eim.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(schema = "inventory", name = "euicc_certs")
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

    public String getEid() {
        return eid;
    }

    public void setEid(String eid) {
        this.eid = eid;
    }

    public String getEuiccCertBase64() {
        return euiccCertBase64;
    }

    public void setEuiccCertBase64(String euiccCertBase64) {
        this.euiccCertBase64 = euiccCertBase64;
    }

    public String getEumCertBase64() {
        return eumCertBase64;
    }

    public void setEumCertBase64(String eumCertBase64) {
        this.eumCertBase64 = eumCertBase64;
    }

    public String getEuiccPublicKeyHex() {
        return euiccPublicKeyHex;
    }

    public void setEuiccPublicKeyHex(String euiccPublicKeyHex) {
        this.euiccPublicKeyHex = euiccPublicKeyHex;
    }

    public String getEuiccSubject() {
        return euiccSubject;
    }

    public void setEuiccSubject(String euiccSubject) {
        this.euiccSubject = euiccSubject;
    }

    public String getEumSubject() {
        return eumSubject;
    }

    public void setEumSubject(String eumSubject) {
        this.eumSubject = eumSubject;
    }

    public String getCiReference() {
        return ciReference;
    }

    public void setCiReference(String ciReference) {
        this.ciReference = ciReference;
    }

    public Instant getCertValidFrom() {
        return certValidFrom;
    }

    public void setCertValidFrom(Instant certValidFrom) {
        this.certValidFrom = certValidFrom;
    }

    public Instant getCertValidTo() {
        return certValidTo;
    }

    public void setCertValidTo(Instant certValidTo) {
        this.certValidTo = certValidTo;
    }

    public boolean isChainValid() {
        return chainValid;
    }

    public void setChainValid(boolean chainValid) {
        this.chainValid = chainValid;
    }
}

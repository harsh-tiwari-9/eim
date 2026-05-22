package com.jio.eim.inventory.service;

import com.jio.eim.inventory.dto.InventoryRequest.EuiccEumCertDto;
import com.jio.eim.inventory.dto.InventoryResponse.CertSummary;
import com.jio.eim.inventory.ingest.CertValidationResult;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CertificateService {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public CertValidationResult validateAndExtractSafe(EuiccEumCertDto certDto) {
        try {
            return CertValidationResult.ok(validateAndExtract(certDto));
        } catch (Exception ex) {
            return CertValidationResult.fail("Invalid certificate data: " + ex.getMessage());
        }
    }

    public CertSummary validateAndExtract(EuiccEumCertDto certDto) {
        try {
            X509Certificate euiccCert = parseCertificate(certDto.getEuiccCertAsBase64());
            X509Certificate eumCert = parseCertificate(certDto.getEumCertAsBase64());

            boolean chainValid = verifyChain(euiccCert, eumCert);
            euiccCert.checkValidity();

            CertSummary summary = new CertSummary();
            summary.setChainValid(chainValid);
            summary.setEuiccSubject(euiccCert.getSubjectX500Principal().getName());
            summary.setEumSubject(eumCert.getSubjectX500Principal().getName());
            summary.setEuiccPublicKeyHex(toHex(euiccCert.getPublicKey()));
            summary.setCertValidFrom(euiccCert.getNotBefore().toInstant());
            summary.setCertValidTo(euiccCert.getNotAfter().toInstant());
            return summary;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid certificate data: " + ex.getMessage());
        }
    }

    private X509Certificate parseCertificate(String base64) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(base64);
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(decoded));
    }

    private boolean verifyChain(X509Certificate euiccCert, X509Certificate eumCert) {
        try {
            euiccCert.verify(eumCert.getPublicKey());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private String toHex(PublicKey publicKey) {
        return HexFormat.of().formatHex(publicKey.getEncoded());
    }
}

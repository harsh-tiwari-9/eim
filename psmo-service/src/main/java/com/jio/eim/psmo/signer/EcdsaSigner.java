package com.jio.eim.psmo.signer;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Signs the {@code toBeSigned} bytes of a eUICC Package with the eIM's ECDSA key (SGP.32 §2.11.1).
 *
 * <p>Produces a raw {@code r || s} signature (IEEE P1363 format, 64 bytes for NIST P-256) as
 * required by SGP.22/SGP.32 — <em>not</em> a DER-wrapped signature. The signing key's public half
 * must be provisioned on the card as {@code eimPublicKeyData}, otherwise the eUICC rejects the
 * package.
 *
 * <p>Active in every profile except {@code lab} (which uses {@link NoOpSigner}).
 */
@Component
@Profile("!lab")
public class EcdsaSigner implements Signer {

    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSAinP1363Format";
    private static final String ALGORITHM_LABEL = "ECDSA-P256";

    private final PrivateKey signingKey;

    public EcdsaSigner(
            @Value("${eim.psmo.signing.keystore:./tls/eim2-signing.p12}") String keystoreLocation,
            @Value("${eim.psmo.signing.password:changeit}") String keystorePassword,
            @Value("${eim.psmo.signing.key-alias:eim2-signing}") String keyAlias,
            @Value("${eim.psmo.signing.keystore-type:PKCS12}") String keystoreType) {
        this.signingKey = loadSigningKey(keystoreLocation, keystorePassword, keyAlias, keystoreType);
    }

    @Override
    public SignatureResult sign(byte[] toBeSigned) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(signingKey);
            signature.update(toBeSigned);
            return new SignatureResult(ALGORITHM_LABEL, signature.sign());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to ECDSA-sign eUICC Package", ex);
        }
    }

    private static PrivateKey loadSigningKey(
            String location, String password, String alias, String type) {
        Resource resource = locationToResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("eIM signing keystore not found at: " + location
                    + " (set eim.psmo.signing.* or use the 'lab' profile)");
        }
        try (InputStream in = resource.getInputStream()) {
            char[] pw = password.toCharArray();
            KeyStore keyStore = KeyStore.getInstance(type);
            keyStore.load(in, pw);
            PrivateKey key = (PrivateKey) keyStore.getKey(alias, pw);
            if (key == null) {
                throw new IllegalStateException("No key found in keystore for alias: " + alias);
            }
            return key;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load eIM signing key from " + location, ex);
        }
    }

    private static Resource locationToResource(String location) {
        // Treat a bare/relative path as a filesystem path; honour explicit Spring prefixes otherwise.
        String resolved = location.contains(":") ? location : "file:" + location;
        return new DefaultResourceLoader().getResource(resolved);
    }
}
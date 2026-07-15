package com.jio.eim.psmo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TLS settings for the device-facing ESipa HTTPS connector (SGP.32 §3.1.2.1).
 *
 * <p>This is a <em>separate</em> connector from the service's main HTTP port: the eUICC's IPAe
 * connects here over HTTPS, while the admin API / api-gateway keep using plain HTTP internally.
 * Disabled by default so local development is unaffected.
 *
 * <p>SGP.32 uses <b>server authentication only</b> (the eUICC authenticates the eIM; no client
 * cert), and the eUICC validates this server certificate against the {@code trustedPublicKeyDataTls}
 * provisioned in the eIM Configuration Data — so the keystore cert must be the one whose key/chain
 * was provisioned via {@code addEim}/{@code updateEim}. The mandatory TLS 1.2 cipher suites are
 * {@code ECDHE_ECDSA}, therefore the certificate key must be EC (ECDSA), e.g. NIST P-256.
 */
@ConfigurationProperties(prefix = "eim.esipa.tls")
public class EsipaTlsProperties {

    /** Enable the dedicated ESipa HTTPS connector. */
    private boolean enabled = false;

    /** Port for the HTTPS connector (distinct from the main HTTP server.port). */
    private int port = 8443;

    /** Filesystem path to the keystore holding the eIM TLS (ECDSA) certificate + private key. */
    private String keystore = "./tls/eim2-tls.p12";

    /** Keystore password. */
    private String password = "changeit";

    /** Keystore type. */
    private String keystoreType = "PKCS12";

    /** Alias of the key entry within the keystore. */
    private String keyAlias = "eim2-tls";

    /** Enabled TLS protocols (SGP.32 §2.6.3.2 mandates TLS 1.2 / 1.3). */
    private String protocols = "TLSv1.2,TLSv1.3";

    /** Enabled cipher suites — defaults to the SGP.32-mandated ECDHE_ECDSA + TLS 1.3 AES-GCM suites. */
    private String ciphers = "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,"
            + "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,"
            + "TLS_AES_128_GCM_SHA256,"
            + "TLS_AES_256_GCM_SHA384";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getKeystore() {
        return keystore;
    }

    public void setKeystore(String keystore) {
        this.keystore = keystore;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getKeystoreType() {
        return keystoreType;
    }

    public void setKeystoreType(String keystoreType) {
        this.keystoreType = keystoreType;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public void setKeyAlias(String keyAlias) {
        this.keyAlias = keyAlias;
    }

    public String getProtocols() {
        return protocols;
    }

    public void setProtocols(String protocols) {
        this.protocols = protocols;
    }

    public String getCiphers() {
        return ciphers;
    }

    public void setCiphers(String ciphers) {
        this.ciphers = ciphers;
    }
}
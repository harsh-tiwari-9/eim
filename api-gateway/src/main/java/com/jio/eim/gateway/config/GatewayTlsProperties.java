package com.jio.eim.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TLS settings for the api-gateway's public HTTPS connector.
 *
 * <p>The gateway is the single entry point for all UI/admin traffic: it terminates TLS and enforces
 * JWT (see {@code JwtAuthFilter}), then forwards to the user/inventory/psmo services over plain HTTP
 * on the internal network. This is a <em>separate</em> connector from the main HTTP port, so 8080
 * stays available for internal/health use while browsers connect over HTTPS.
 *
 * <p>Unlike the psmo ESipa connector (which is locked to the SGP.32-mandated ECDHE_ECDSA suites for
 * the eUICC), this connector serves ordinary web clients, so the cipher list is left to the
 * platform default unless explicitly overridden — that keeps it browser-compatible. Disabled by
 * default so local development is unaffected.
 */
@ConfigurationProperties(prefix = "eim.gateway.tls")
public class GatewayTlsProperties {

    /** Enable the public HTTPS connector. */
    private boolean enabled = false;

    /** Port for the HTTPS connector (distinct from the main HTTP server.port, and from psmo's 8443). */
    private int port = 9443;

    /** Filesystem path to the keystore holding the gateway's TLS certificate + private key. */
    private String keystore = "./tls/eim2-tls.p12";

    /** Keystore password. */
    private String password = "changeit";

    /** Keystore type. */
    private String keystoreType = "PKCS12";

    /** Alias of the key entry within the keystore. */
    private String keyAlias = "eim2-tls";

    /** Enabled TLS protocols. */
    private String protocols = "TLSv1.2,TLSv1.3";

    /** Optional explicit cipher suite list; when blank, the platform/Tomcat default is used. */
    private String ciphers = "";

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
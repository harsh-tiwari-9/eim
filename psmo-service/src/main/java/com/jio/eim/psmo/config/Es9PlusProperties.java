package com.jio.eim.psmo.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the eIM's ES9+ client used by the indirect profile-download relay (SGP.32 §6.3.2).
 * The eIM forwards the eUICC's RSP handshake to the SM-DP+ over the SGP.22 ES9+ JSON/REST binding.
 * TLS trust comes from a dedicated truststore because SM-DP+ certificates are issued by the GSMA
 * RSP CI, not a public web CA.
 */
@ConfigurationProperties(prefix = "eim.esipa.smdp")
public class Es9PlusProperties {

    /** Comma-separated allowlist of SM-DP+ hostnames the eIM may relay to (guards against SSRF). */
    private String allowed = "SMDPRD.JIO.COM";

    /** ES9+ base path on the SM-DP+. */
    private String basePath = "/gsma/rsp2/es9plus";

    /** Value of the X-Admin-Protocol header. */
    private String adminProtocol = "gsma/rsp/v2.3.0";

    /** PKCS12 truststore holding the SM-DP+ server cert (or its CI root). */
    private String truststore = "./es9-truststore.p12";

    private String truststorePassword = "changeit";

    private int connectTimeoutMs = 10_000;

    private int readTimeoutMs = 60_000;

    /** Lower-cased set of allowed hosts. */
    public Set<String> allowedHosts() {
        return Arrays.stream(allowed.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase())
                .collect(Collectors.toSet());
    }

    public String getAllowed() { return allowed; }
    public void setAllowed(String allowed) { this.allowed = allowed; }
    public String getBasePath() { return basePath; }
    public void setBasePath(String basePath) { this.basePath = basePath; }
    public String getAdminProtocol() { return adminProtocol; }
    public void setAdminProtocol(String adminProtocol) { this.adminProtocol = adminProtocol; }
    public String getTruststore() { return truststore; }
    public void setTruststore(String truststore) { this.truststore = truststore; }
    public String getTruststorePassword() { return truststorePassword; }
    public void setTruststorePassword(String truststorePassword) { this.truststorePassword = truststorePassword; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
}
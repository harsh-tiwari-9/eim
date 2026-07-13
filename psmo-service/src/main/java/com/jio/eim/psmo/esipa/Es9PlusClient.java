package com.jio.eim.psmo.esipa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jio.eim.psmo.config.Es9PlusProperties;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Minimal ES9+ client (SGP.22 JSON/REST binding) used by the indirect download relay. Posts JSON to
 * {@code https://<smdp><basePath>/<function>} and returns the parsed response. TLS trust comes from
 * a dedicated truststore because SM-DP+ certs are issued by the GSMA RSP CI, not a public web CA.
 *
 * <p>The eIM only forwards to hosts on the configured allowlist (SSRF guard) — the {@code
 * smdpAddress} in an ESipa message is attacker-influenceable, so it is validated before any call.
 */
@Component
@Slf4j
@EnableConfigurationProperties(Es9PlusProperties.class)
public class Es9PlusClient {

    private final Es9PlusProperties props;
    private final ObjectMapper objectMapper;
    private final Set<String> allowedHosts;
    private final HttpClient httpClient;

    public Es9PlusClient(Es9PlusProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.allowedHosts = props.allowedHosts();
        this.httpClient = buildHttpClient(props);
    }

    /** POST a JSON body to an ES9+ function and return the parsed JSON response. */
    public JsonNode call(String smdpAddress, String function, JsonNode body) {
        String host = validateHost(smdpAddress);
        URI uri = URI.create("https://" + host + props.getBasePath() + "/" + function);
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(props.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-Admin-Protocol", props.getAdminProtocol())
                    .header("User-Agent", "jio-eim/1.0 (ES9+ relay)")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            log.info("ES9+ -> {} {}", function, uri);
            // TEMP diagnostic: dump the exact request body so a SM-DP+ format rejection can be traced.
            log.info("ES9+ {} request body: {}", function, body.toString());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("ES9+ {} returned HTTP {} : {}", function, response.statusCode(),
                        truncate(response.body()));
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "SM-DP+ " + function + " HTTP " + response.statusCode());
            }
            // TEMP diagnostic: response head (shows transactionId case + fields).
            log.info("ES9+ {} response head: {}", function, truncate(response.body()));
            return objectMapper.readTree(response.body());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "ES9+ call to SM-DP+ failed: " + function + " — " + ex.getMessage(), ex);
        }
    }

    /** @return the bare host (no port) if allowed; otherwise 400. */
    private String validateHost(String smdpAddress) {
        if (smdpAddress == null || smdpAddress.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "smdpAddress is required");
        }
        String host = smdpAddress.trim().toLowerCase();
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        if (!allowedHosts.contains(host)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "SM-DP+ host not allowlisted: " + smdpAddress);
        }
        return host;
    }

    private static HttpClient buildHttpClient(Es9PlusProperties props) {
        try {
            if (!Files.exists(Path.of(props.getTruststore()))) {
                log.warn("ES9+ truststore {} not found — using default TLS trust; SM-DP+ calls will "
                        + "fail until the SM-DP+ CI cert is trusted.", props.getTruststore());
                return defaultClient(props);
            }
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            try (InputStream in = new FileInputStream(props.getTruststore())) {
                trustStore.load(in, props.getTruststorePassword().toCharArray());
            }
            TrustManagerFactory tmf =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);
            log.info("ES9+ client initialised with truststore {}", props.getTruststore());
            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                    .build();
        } catch (Exception ex) {
            log.error("Failed to build ES9+ TLS context from {} — falling back to default trust",
                    props.getTruststore(), ex);
            return defaultClient(props);
        }
    }

    private static HttpClient defaultClient(Es9PlusProperties props) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .build();
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
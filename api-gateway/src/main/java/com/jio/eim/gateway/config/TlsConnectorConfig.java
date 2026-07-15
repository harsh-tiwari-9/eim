package com.jio.eim.gateway.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Adds a public HTTPS connector to the api-gateway, in addition to its main HTTP connector. This is
 * the single TLS entry point for the UI: browsers connect here over HTTPS, {@code JwtAuthFilter}
 * validates the token, and requests are proxied to the backend services over internal HTTP.
 *
 * <p>Only active when {@code eim.gateway.tls.enabled=true} — off for local development, on for the
 * public deployment. Uses server authentication only (no client certificate).
 */
@Configuration
@EnableConfigurationProperties(GatewayTlsProperties.class)
public class TlsConnectorConfig {

    @Bean
    @ConditionalOnProperty(prefix = "eim.gateway.tls", name = "enabled", havingValue = "true")
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> gatewayHttpsConnector(GatewayTlsProperties props) {
        return factory -> factory.addAdditionalTomcatConnectors(buildHttpsConnector(props));
    }

    private Connector buildHttpsConnector(GatewayTlsProperties props) {
        Connector connector = new Connector(Http11NioProtocol.class.getName());
        connector.setPort(props.getPort());
        connector.setScheme("https");
        connector.setSecure(true);

        Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
        protocol.setSSLEnabled(true);

        SSLHostConfig sslHostConfig = new SSLHostConfig();
        sslHostConfig.setProtocols(props.getProtocols());
        // Leave ciphers at the platform default unless explicitly set, so ordinary web clients
        // (browsers) negotiate a compatible suite for whatever cert the keystore holds.
        if (StringUtils.hasText(props.getCiphers())) {
            sslHostConfig.setCiphers(props.getCiphers());
        }
        // Server authentication only: UI clients don't present a client certificate.
        sslHostConfig.setCertificateVerification("none");

        SSLHostConfigCertificate certificate = new SSLHostConfigCertificate(sslHostConfig, Type.UNDEFINED);
        certificate.setCertificateKeystoreFile(props.getKeystore());
        certificate.setCertificateKeystorePassword(props.getPassword());
        certificate.setCertificateKeystoreType(props.getKeystoreType());
        certificate.setCertificateKeyAlias(props.getKeyAlias());
        sslHostConfig.addCertificate(certificate);

        protocol.addSslHostConfig(sslHostConfig);
        return connector;
    }
}
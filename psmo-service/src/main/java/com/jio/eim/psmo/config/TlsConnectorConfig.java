package com.jio.eim.psmo.config;

import com.jio.eim.psmo.filter.EsipaPortIsolationFilter;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.SSLHostConfigCertificate.Type;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Adds a dedicated HTTPS connector for device-facing ESipa traffic (SGP.32 §3.1.2.1), in addition
 * to the service's main HTTP connector. The eUICC's IPAe connects here over TLS; the admin API and
 * api-gateway continue to use plain HTTP internally.
 *
 * <p>Only active when {@code eim.esipa.tls.enabled=true} — keep it off for local development and
 * turn it on for the public eim2 deployment.
 */
@Configuration
@EnableConfigurationProperties(EsipaTlsProperties.class)
public class TlsConnectorConfig {

    @Bean
    @ConditionalOnProperty(prefix = "eim.esipa.tls", name = "enabled", havingValue = "true")
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> esipaHttpsConnector(EsipaTlsProperties props) {
        return factory -> factory.addAdditionalTomcatConnectors(buildHttpsConnector(props));
    }

    /**
     * When the public ESipa port is active, keep the admin API ({@code /api/**}) off it — only the
     * device-facing paths are served there. Admin traffic must come via the api-gateway (internal
     * HTTP port), which enforces JWT. Runs before Spring Security so blocked calls never touch auth.
     */
    @Bean
    @ConditionalOnProperty(prefix = "eim.esipa.tls", name = "enabled", havingValue = "true")
    FilterRegistrationBean<EsipaPortIsolationFilter> esipaPortIsolationFilter(EsipaTlsProperties props) {
        FilterRegistrationBean<EsipaPortIsolationFilter> registration =
                new FilterRegistrationBean<>(new EsipaPortIsolationFilter(props.getPort()));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    private Connector buildHttpsConnector(EsipaTlsProperties props) {
        Connector connector = new Connector(Http11NioProtocol.class.getName());
        connector.setPort(props.getPort());
        connector.setScheme("https");
        connector.setSecure(true);

        Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
        protocol.setSSLEnabled(true);

        SSLHostConfig sslHostConfig = new SSLHostConfig();
        sslHostConfig.setProtocols(props.getProtocols());
        sslHostConfig.setCiphers(props.getCiphers());
        // Server authentication only (SGP.32 §3.1.2.1): the eUICC authenticates the eIM; no client cert.
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
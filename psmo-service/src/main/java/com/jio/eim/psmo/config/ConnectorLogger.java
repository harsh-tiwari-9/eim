package com.jio.eim.psmo.config;

import java.lang.reflect.Field;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * Logs every bound Tomcat connector at startup, including the additional device-facing ESipa HTTPS
 * connector added by {@link TlsConnectorConfig}. Spring Boot's default "Tomcat started on port(s)"
 * message does not always list additional connectors, so this makes it unambiguous whether the
 * HTTPS listener is actually up (handy when deploying to a server such as Azure).
 */
@Component
@Slf4j
public class ConnectorLogger implements ApplicationListener<WebServerInitializedEvent> {

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        if (!(event.getWebServer() instanceof TomcatWebServer tomcatWebServer)) {
            return;
        }
        try {
            Field field = TomcatWebServer.class.getDeclaredField("tomcat");
            field.setAccessible(true);
            Tomcat tomcat = (Tomcat) field.get(tomcatWebServer);
            for (Connector connector : tomcat.getService().findConnectors()) {
                int port = connector.getLocalPort() > 0 ? connector.getLocalPort() : connector.getPort();
                log.info("Listening: {} on port {} (secure={})",
                        connector.getScheme(), port, connector.getSecure());
            }
        } catch (ReflectiveOperationException ex) {
            log.warn("Could not enumerate Tomcat connectors for startup logging: {}", ex.getMessage());
        }
    }
}

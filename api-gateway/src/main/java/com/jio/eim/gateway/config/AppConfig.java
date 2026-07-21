package com.jio.eim.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        // The gateway is a transparent proxy: downstream 4xx/5xx must be relayed to the client with
        // their real status + body, not thrown. The default error handler throws on any non-2xx,
        // which the proxy would surface as a generic 500 (e.g. a 401 login failure). A no-op handler
        // lets exchange() return the actual ResponseEntity so ProxyController can pass it through.
        restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse response) {
                // no-op — status/body are relayed by ProxyController
            }
        });
        return restTemplate;
    }
}

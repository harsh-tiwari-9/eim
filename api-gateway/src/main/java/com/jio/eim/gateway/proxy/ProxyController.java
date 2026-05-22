package com.jio.eim.gateway.proxy;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
public class ProxyController {

    private final RestTemplate restTemplate;
    private final String userServiceUrl;
    private final String inventoryServiceUrl;

    public ProxyController(
            RestTemplate restTemplate,
            @Value("${eim.services.user-url}") String userServiceUrl,
            @Value("${eim.services.inventory-url}") String inventoryServiceUrl) {
        this.restTemplate = restTemplate;
        this.userServiceUrl = trimTrailingSlash(userServiceUrl);
        this.inventoryServiceUrl = trimTrailingSlash(inventoryServiceUrl);
    }

    @RequestMapping("/**")
    public ResponseEntity<String> proxy(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI();
        String baseUrl = resolveBaseUrl(path);
        String query = request.getQueryString();
        String targetUrl = baseUrl + path + (query != null ? "?" + query : "");

        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        HttpHeaders headers = copyHeaders(request);

        ResponseEntity<byte[]> response;
        if (request instanceof MultipartHttpServletRequest multipartRequest) {
            response = forwardMultipart(targetUrl, method, headers, multipartRequest);
        } else {
            byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
            HttpEntity<byte[]> entity = body.length == 0
                    ? new HttpEntity<>(headers)
                    : new HttpEntity<>(body, headers);
            response = restTemplate.exchange(URI.create(targetUrl), method, entity, byte[].class);
        }

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);

        String responseBody = response.getBody() == null
                ? ""
                : new String(response.getBody(), StandardCharsets.UTF_8);

        return new ResponseEntity<>(responseBody, responseHeaders, response.getStatusCode());
    }

    private String resolveBaseUrl(String path) {
        if (path.startsWith("/api/auth/") || path.startsWith("/api/users")) {
            return userServiceUrl;
        }
        if (path.startsWith("/api/inventory")) {
            return inventoryServiceUrl;
        }
        throw new IllegalArgumentException("No route for path: " + path);
    }

    private ResponseEntity<byte[]> forwardMultipart(
            String targetUrl,
            HttpMethod method,
            HttpHeaders headers,
            MultipartHttpServletRequest multipartRequest) throws IOException {
        headers.remove(HttpHeaders.CONTENT_TYPE);
        headers.remove(HttpHeaders.CONTENT_LENGTH);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (Map.Entry<String, MultipartFile> entry : multipartRequest.getFileMap().entrySet()) {
            MultipartFile file = entry.getValue();
            if (file == null || file.isEmpty()) {
                continue;
            }
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add(entry.getKey(), resource);
        }

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(URI.create(targetUrl), method, entity, byte[].class);
    }

    private HttpHeaders copyHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            // Skip host (wrong target) and content-length (RestTemplate recalculates).
            // Keep Content-Type for JSON/login; multipart path strips it in forwardMultipart().
            if ("host".equalsIgnoreCase(name) || "content-length".equalsIgnoreCase(name)) {
                continue;
            }
            headers.put(name, Collections.list(request.getHeaders(name)));
        }
        return headers;
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

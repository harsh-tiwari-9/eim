package com.jio.eim.psmo.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs every inbound HTTP request that reaches this service — including ESipa device polls and any
 * request that ends in 404/415/501 — so it is easy to confirm whether the eUICC's IPAe is actually
 * hitting eim2 (see the "zero requests reaching eim2" diagnosis).
 *
 * <p>Registered with highest precedence so it wraps the whole chain (auth, routing) and can report
 * the final status + duration.
 *
 * <p>Caveat: this is application-level. A failed TLS handshake (e.g. a certificate that does not
 * match the provisioned {@code trustedPublicKeyDataTls}) never reaches the servlet and so will NOT
 * appear here — check Tomcat/connector logs for those.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        long start = System.currentTimeMillis();
        String query = request.getQueryString();
        String path = request.getRequestURI() + (query != null ? "?" + query : "");

        log.info("--> {} {} from {} (scheme={}, content-type={}, length={})",
                request.getMethod(),
                path,
                clientAddress(request),
                request.getScheme(),
                request.getContentType(),
                request.getContentLengthLong());

        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info("<-- {} {} status={} ({} ms)",
                    request.getMethod(),
                    path,
                    response.getStatus(),
                    System.currentTimeMillis() - start);
        }
    }

    /** Prefer the real client IP when behind a proxy/load balancer, else the socket peer. */
    private static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim() + " (via " + request.getRemoteAddr() + ")";
        }
        return request.getRemoteAddr();
    }
}
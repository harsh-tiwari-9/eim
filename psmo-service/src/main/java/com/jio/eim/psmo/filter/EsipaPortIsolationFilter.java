package com.jio.eim.psmo.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Restricts what is reachable on the public, device-facing ESipa HTTPS port.
 *
 * <p>That connector is exposed to the internet so the eUICC/IPA can poll it, and psmo-service
 * authenticates admin calls only via trusted {@code X-User-*} headers injected by the api-gateway.
 * Without this filter the admin API ({@code /api/**}) would also be reachable on the public port
 * with no real authentication — anyone could send {@code X-User-Role: SUPER_ADMIN} and drive PSMO
 * operations. So on the ESipa port we allow ONLY the SGP.32 device-facing paths (and health);
 * everything else returns {@code 404}. Admin traffic must go through the api-gateway on the
 * internal HTTP port, where JWT is enforced.
 */
public class EsipaPortIsolationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(EsipaPortIsolationFilter.class);

    private final int esipaPort;

    public EsipaPortIsolationFilter(int esipaPort) {
        this.esipaPort = esipaPort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (request.getLocalPort() == esipaPort && !isDevicePath(request.getRequestURI())) {
            log.warn("Blocked non-ESipa path {} on public ESipa port {} (from {})",
                    request.getRequestURI(), esipaPort, request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Only the SGP.32 ESipa endpoints (and health checks) are served on the public port. */
    private boolean isDevicePath(String uri) {
        return uri.startsWith("/gsma/")
                || uri.startsWith("/esipa/")
                || uri.equals("/actuator/health")
                || uri.startsWith("/actuator/health/");
    }
}
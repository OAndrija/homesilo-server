package com.andrija.homesiloserver.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Refuses requests that change something when a browser says another site sent
 * them, using {@code Sec-Fetch-Site} (every current browser sends it) and, failing
 * that, {@code Origin}. Requests with neither come from scripts and API clients,
 * not from a page, so they pass.
 *
 * <p>This backs up the CSRF token everywhere, and covers what the token can't:
 * the sign-in, sign-up and setup forms, which run before there is a session to
 * hold one. Without it, another site could sign a visitor into the attacker's
 * account ("login CSRF"), so what they upload lands there.
 */
public class CrossSiteRequestFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final String SEC_FETCH_SITE = "Sec-Fetch-Site";

    /** Origins allowed by the CORS setting: a client served from elsewhere on purpose. */
    private final Set<String> trustedOrigins;

    public CrossSiteRequestFilter(Collection<String> trustedOrigins) {
        this.trustedOrigins = trustedOrigins.stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .map(CrossSiteRequestFilter::normalizeOrigin)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isCrossSite(request)) {
            ProblemResponses.write(response, HttpStatus.FORBIDDEN,
                    "This request came from another website, so it was blocked");
            return;
        }
        chain.doFilter(request, response);
    }

    boolean isCrossSite(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && trustedOrigins.contains(normalizeOrigin(origin))) return false;

        String site = request.getHeader(SEC_FETCH_SITE);
        if (site != null) {
            // "none": typed in the address bar or a bookmark. "same-site" (a sibling
            // subdomain) is still another site's page, so only an exact match passes.
            return !site.equals("same-origin") && !site.equals("none");
        }
        if (origin == null || origin.equals("null")) {
            // No browser signal at all: a script or API client. An opaque "null"
            // origin, though, is a sandboxed page or a redirect: treat it as foreign.
            return origin != null;
        }
        // Older browsers: compare the Origin's host with the Host the request was sent to.
        // Only the host and port: a TLS-terminating proxy in front may make the scheme differ.
        String host = request.getHeader(HttpHeaders.HOST);
        return host == null || !authority(origin).equals(stripDefaultPort(host.toLowerCase(Locale.ROOT)));
    }

    private static String authority(String origin) {
        try {
            URI uri = URI.create(origin.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return uri.getPort() == -1 || uri.getPort() == 80 || uri.getPort() == 443
                    ? host
                    : host + ':' + uri.getPort();
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private static String stripDefaultPort(String host) {
        return host.endsWith(":80") ? host.substring(0, host.length() - 3)
                : host.endsWith(":443") ? host.substring(0, host.length() - 4)
                : host;
    }

    private static String normalizeOrigin(String origin) {
        String trimmed = origin.trim().toLowerCase(Locale.ROOT);
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}

package com.andrija.homesiloserver.security;

import com.andrija.homesiloserver.auth.AuthCookies;
import com.andrija.homesiloserver.auth.JwtService;
import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Authenticates requests from the session cookie (browser) or an
 * {@code Authorization: Bearer} header (scripts / API clients).
 *
 * <p>The user is re-loaded on every request — a single primary-key lookup — so
 * disabling an account or changing a password takes effect immediately instead
 * of when the token expires.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AuthCookies authCookies;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.parse(token)
                    .flatMap(claims -> userRepository.findById(claims.userId())
                            .filter(User::isEnabled)
                            .filter(user -> !isRevoked(user, claims.issuedAt())))
                    .ifPresent(user -> authenticate(request, user));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, User user) {
        ServerUserDetails principal = new ServerUserDetails(user);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /** A token is revoked if it was issued before the user's last password change (JWT iat has second precision). */
    private static boolean isRevoked(User user, Instant issuedAt) {
        if (user.getPasswordChangedAt() == null) return false;
        Instant changedAt = user.getPasswordChangedAt()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .truncatedTo(ChronoUnit.SECONDS);
        return issuedAt.isBefore(changedAt);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return authCookies.readSessionToken(request);
    }
}

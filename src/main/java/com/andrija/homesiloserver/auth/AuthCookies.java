package com.andrija.homesiloserver.auth;

import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.user.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/**
 * The session token lives in an {@code HttpOnly} cookie so page scripts (and
 * therefore XSS payloads) can never read it. {@code SameSite=Lax} plus the CSRF
 * token protect state-changing requests.
 */
@Component
public class AuthCookies {

    private final JwtService jwtService;
    private final String cookieName;
    /** Null: decided per request, by whether it came over HTTPS. */
    private final Boolean secure;

    public AuthCookies(JwtService jwtService, HomesiloProperties properties) {
        this.jwtService = jwtService;
        this.cookieName = properties.auth().cookieName();
        this.secure = properties.auth().cookieSecure();
    }

    public ResponseCookie sessionCookie(User user) {
        return baseCookie(jwtService.issue(user))
                .maxAge(jwtService.expiration())
                .build();
    }

    public ResponseCookie clearedCookie() {
        return baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
    }

    public String readSessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure != null ? secure : cameOverHttps())
                .sameSite("Lax")
                .path("/");
    }

    /** Behind a proxy, Tomcat takes this from X-Forwarded-Proto (server.forward-headers-strategy). */
    private static boolean cameOverHttps() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getRequest().isSecure();
    }
}

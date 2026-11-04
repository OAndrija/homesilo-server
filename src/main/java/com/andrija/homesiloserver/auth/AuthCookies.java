package com.andrija.homesiloserver.auth;

import com.andrija.homesiloserver.common.config.HomesiloProperties;
import com.andrija.homesiloserver.user.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

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
    private final boolean secure;

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
                .secure(secure)
                .sameSite("Lax")
                .path("/");
    }
}

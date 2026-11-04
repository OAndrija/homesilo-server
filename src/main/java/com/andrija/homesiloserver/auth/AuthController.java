package com.andrija.homesiloserver.auth;

import com.andrija.homesiloserver.auth.dto.AuthConfigResponse;
import com.andrija.homesiloserver.auth.dto.AuthResponse;
import com.andrija.homesiloserver.auth.dto.LoginRequest;
import com.andrija.homesiloserver.auth.dto.RegisterRequest;
import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookies authCookies;
    private final JwtService jwtService;

    @GetMapping("/config")
    public AuthConfigResponse config() {
        return new AuthConfigResponse(authService.isRegistrationOpen());
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withSession(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return withSession(HttpStatus.OK, authService.login(request, httpRequest.getRemoteAddr()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookies.clearedCookie().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> withSession(HttpStatus status, User user) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, authCookies.sessionCookie(user).toString())
                .body(new AuthResponse(UserResponse.from(user), jwtService.expiration().toSeconds()));
    }
}

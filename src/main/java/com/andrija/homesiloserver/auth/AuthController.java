package com.andrija.homesiloserver.auth;

import com.andrija.homesiloserver.activity.ActivityAction;
import com.andrija.homesiloserver.activity.ActivityRecorder;
import com.andrija.homesiloserver.auth.dto.AuthConfigResponse;
import com.andrija.homesiloserver.auth.dto.AuthResponse;
import com.andrija.homesiloserver.auth.dto.LoginRequest;
import com.andrija.homesiloserver.auth.dto.RegisterRequest;
import com.andrija.homesiloserver.auth.dto.SetupRequest;
import com.andrija.homesiloserver.invite.InviteService;
import com.andrija.homesiloserver.invite.dto.InvitePreviewResponse;
import com.andrija.homesiloserver.invite.dto.InviteTokenRequest;
import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final SetupService setupService;
    private final InviteService inviteService;
    private final AuthCookies authCookies;
    private final JwtService jwtService;
    private final ActivityRecorder activity;

    @GetMapping("/config")
    public AuthConfigResponse config() {
        return new AuthConfigResponse(authService.isRegistrationOpen(), setupService.isSetupRequired());
    }

    /** Creates the first admin on a fresh install, with the setup code from the server log. */
    @PostMapping("/setup")
    public ResponseEntity<AuthResponse> setUp(@Valid @RequestBody SetupRequest request, HttpServletRequest httpRequest) {
        return withSession(HttpStatus.CREATED, setupService.setUp(request, httpRequest.getRemoteAddr()));
    }

    /** Whether an invite link still works, and who sent it, for the sign-up page. */
    @PostMapping("/invites/preview")
    public InvitePreviewResponse invite(@Valid @RequestBody InviteTokenRequest request) {
        return inviteService.preview(request.token().trim());
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
    public ResponseEntity<Void> logout(@AuthenticationPrincipal ServerUserDetails principal) {
        if (principal != null) activity.recordAs(principal.getUser(), ActivityAction.SIGNED_OUT);
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

package com.andrija.homesiloserver.user;

import com.andrija.homesiloserver.auth.AuthCookies;
import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.user.dto.ChangePasswordRequest;
import com.andrija.homesiloserver.user.dto.UpdateUserRequest;
import com.andrija.homesiloserver.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthCookies authCookies;

    @GetMapping("/me")
    public UserResponse getCurrentUser(@AuthenticationPrincipal ServerUserDetails principal) {
        return userService.findById(principal.getId());
    }

    /** Changes the password and re-issues the session cookie; every other session is invalidated. */
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        User user = userService.changePassword(principal.getId(), request);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookies.sessionCookie(user).toString())
                .build();
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> listAllUsers() {
        return userService.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse getUserById(@PathVariable UUID id) {
        return userService.findById(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return userService.update(principal.getId(), id, request);
    }
}

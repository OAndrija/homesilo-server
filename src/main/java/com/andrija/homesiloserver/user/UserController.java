package com.andrija.homesiloserver.user;

import com.andrija.homesiloserver.auth.AuthCookies;
import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.user.dto.ChangePasswordRequest;
import com.andrija.homesiloserver.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


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
}

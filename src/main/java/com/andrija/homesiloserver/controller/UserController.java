package com.andrija.homesiloserver.controller;

import com.andrija.homesiloserver.dto.UserResponse;
import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal ServerUserDetails userDetails) {
        UserResponse userResponse = userService.findUserById(userDetails.getId());
        return ResponseEntity.ok(userResponse);
    }

    // Returns a list of all users
    @GetMapping
    public ResponseEntity<List<UserResponse>> listAllUsers() {
        List<UserResponse> usersResponse = userService.listAllUsers();
        return ResponseEntity.ok(usersResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        UserResponse userResponse = userService.findUserById(id);
        return ResponseEntity.ok(userResponse);
    }

    @GetMapping("/search")
    public ResponseEntity<UserResponse> searchUser(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email
    ) {
        if (username != null) return ResponseEntity.ok(userService.findUserByUsername(username));
        if (email != null) return ResponseEntity.ok(userService.findUserByEmail(email));
        return ResponseEntity.badRequest().build();
    }
}

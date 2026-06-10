package com.andrija.homesiloserver.controller;

import com.andrija.homesiloserver.dto.UserRegisterRequest;
import com.andrija.homesiloserver.dto.UserResponse;
import com.andrija.homesiloserver.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Registers user
    @PostMapping
    public ResponseEntity<UserResponse> registerUser(
            @Valid @RequestBody UserRegisterRequest userRegisterRequest
    ) {
        UserResponse response = userService.registerUser(userRegisterRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Returns a list of all users
    @GetMapping
    public ResponseEntity<List<UserResponse>> listAllUsers() {
        List<UserResponse> users = userService.listAllUsers();
        return ResponseEntity.status(HttpStatus.OK).body(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        UserResponse user = userService.findUserById(id);
        return ResponseEntity.status(HttpStatus.OK).body(user);
    }

    @GetMapping("/search/username")
    public ResponseEntity<UserResponse> getUserByUsername(@RequestParam("value")  String username) {
        UserResponse user = userService.findUserByUsername(username);
        return ResponseEntity.status(HttpStatus.OK).body(user);
    }

    @GetMapping("/search/email")
    public ResponseEntity<UserResponse> getUserByEmail(@RequestParam("value")  String email) {
        UserResponse user = userService.findUserByEmail(email);
        return ResponseEntity.status(HttpStatus.OK).body(user);
    }
}

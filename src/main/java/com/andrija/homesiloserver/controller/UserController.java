package com.andrija.homesiloserver.controller;

import com.andrija.homesiloserver.dto.UserRegisterRequest;
import com.andrija.homesiloserver.dto.UserResponse;
import com.andrija.homesiloserver.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> registerUser(
            @Valid @RequestBody UserRegisterRequest userRegisterRequest
    ) {
        UserResponse response = userService.registerUser(userRegisterRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

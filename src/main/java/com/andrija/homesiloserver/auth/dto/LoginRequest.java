package com.andrija.homesiloserver.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code username} accepts either the username or the email address. */
public record LoginRequest(
        @NotBlank(message = "Username is required")
        @Size(max = 255)
        String username,

        @NotBlank(message = "Password is required")
        @Size(max = 128)
        String password
) {}

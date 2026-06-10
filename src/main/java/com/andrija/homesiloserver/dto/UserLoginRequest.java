package com.andrija.homesiloserver.dto;

import jakarta.validation.constraints.NotBlank;

public record UserLoginRequest(
        @NotBlank(message = ERROR_MESSAGE_IDENTITY_REQUIRED)
        String username,

        @NotBlank(message = ERROR_MESSAGE_PASSWORD_REQUIRED)
        String password
) {
    private static final String ERROR_MESSAGE_IDENTITY_REQUIRED = "Username is required";
    private static final String ERROR_MESSAGE_PASSWORD_REQUIRED = "Password is required";
}

package com.andrija.homesiloserver.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRegisterRequest(
        @NotBlank(message = ERROR_MESSAGE_USERNAME_REQUIRED)
        @Size(max = 255, message = ERROR_MESSAGE_USERNAME_LENGTH)
        String username,

        @NotBlank(message = ERROR_MESSAGE_PASSWORD_REQUIRED)
        @Size(min = 6, max = 255, message = ERROR_MESSAGE_PASSWORD_LENGTH)
        String password,

        @NotBlank(message = ERROR_MESSAGE_EMAIL_REQUIRED)
        @Email(message = ERROR_MESSAGE_EMAIL_VALID)
        String email
) {
    private static final String ERROR_MESSAGE_USERNAME_REQUIRED = "Username is required";
    private static final String ERROR_MESSAGE_USERNAME_LENGTH = "Username must be between 1 and 255 characters";
    private static final String ERROR_MESSAGE_PASSWORD_LENGTH = "Password must be between 6 and 255 characters";
    private static final String ERROR_MESSAGE_PASSWORD_REQUIRED = "Password is required";
    private static final String ERROR_MESSAGE_EMAIL_VALID = "Please provide valid email address";
    private static final String ERROR_MESSAGE_EMAIL_REQUIRED = "Email address is required";
}

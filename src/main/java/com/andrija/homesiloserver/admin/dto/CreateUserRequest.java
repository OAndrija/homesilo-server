package com.andrija.homesiloserver.admin.dto;

import com.andrija.homesiloserver.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** An account added by an admin. Same rules as sign-up; {@code storageQuotaBytes} defaults to the server's default. */
public record CreateUserRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 64, message = "Username must be between 3 and 64 characters")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                message = "Username may only contain letters, numbers, dots, dashes and underscores")
        String username,

        @NotBlank(message = "Email address is required")
        @Email(message = "Please provide a valid email address")
        @Size(max = 255, message = "Email address is too long")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String password,

        @NotNull(message = "Role is required")
        UserRole role,

        @PositiveOrZero(message = "Storage limit must not be negative")
        Long storageQuotaBytes
) {}

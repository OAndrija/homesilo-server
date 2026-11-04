package com.andrija.homesiloserver.user.dto;

import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.UserRole;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        UserRole role,
        boolean enabled,
        LocalDateTime createdAt,
        long storageQuotaBytes
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getStorageQuotaBytes()
        );
    }
}

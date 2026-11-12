package com.andrija.homesiloserver.admin.dto;

import com.andrija.homesiloserver.user.User;
import com.andrija.homesiloserver.user.UserRole;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One account as the admin's user list shows it.
 *
 * @param storageUsedBytes everything counted against the quota, trash included
 * @param fileCount        files outside the trash
 * @param lastLoginAt      null if they have never signed in
 */
public record AdminUserResponse(
        UUID id,
        String username,
        String email,
        UserRole role,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt,
        long storageQuotaBytes,
        long storageUsedBytes,
        long fileCount
) {
    public static AdminUserResponse from(User user, long storageUsedBytes, long fileCount) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                user.getStorageQuotaBytes(),
                storageUsedBytes,
                fileCount
        );
    }
}

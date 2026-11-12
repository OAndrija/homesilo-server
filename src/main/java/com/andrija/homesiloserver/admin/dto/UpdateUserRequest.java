package com.andrija.homesiloserver.admin.dto;

import com.andrija.homesiloserver.user.UserRole;
import jakarta.validation.constraints.PositiveOrZero;

/** Partial update by an admin. Null fields are left unchanged. */
public record UpdateUserRequest(
        Boolean enabled,
        UserRole role,
        @PositiveOrZero(message = "Storage limit must not be negative")
        Long storageQuotaBytes
) {}

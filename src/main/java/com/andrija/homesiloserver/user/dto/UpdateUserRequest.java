package com.andrija.homesiloserver.user.dto;

import com.andrija.homesiloserver.user.UserRole;
import jakarta.validation.constraints.PositiveOrZero;

/** Admin-only partial update. Null fields are left unchanged. */
public record UpdateUserRequest(
        Boolean enabled,
        UserRole role,
        @PositiveOrZero(message = "Quota must not be negative")
        Long storageQuotaBytes
) {}

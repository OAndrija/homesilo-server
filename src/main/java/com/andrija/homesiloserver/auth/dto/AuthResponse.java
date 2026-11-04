package com.andrija.homesiloserver.auth.dto;

import com.andrija.homesiloserver.user.dto.UserResponse;

/** The session token itself travels in an HttpOnly cookie, never in the body. */
public record AuthResponse(
        UserResponse user,
        long expiresInSeconds
) {}

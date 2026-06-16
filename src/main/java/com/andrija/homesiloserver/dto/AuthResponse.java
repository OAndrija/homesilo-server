package com.andrija.homesiloserver.dto;

public record AuthResponse(
        String token,
        long expiresIn
) {}

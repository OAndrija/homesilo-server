package com.andrija.homesiloserver.invite.dto;

import com.andrija.homesiloserver.user.UserRole;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * @param expiresInDays how long the link works; 7 if not given
 * @param role          what the new account will be; a regular user if not given
 * @param note          who it's for, to tell invites apart in the list
 */
public record CreateInviteRequest(
        @Min(value = 1, message = "An invite lasts between 1 and 30 days")
        @Max(value = 30, message = "An invite lasts between 1 and 30 days")
        Integer expiresInDays,
        UserRole role,
        @Size(max = 80, message = "Keep the note under 80 characters")
        String note
) {}

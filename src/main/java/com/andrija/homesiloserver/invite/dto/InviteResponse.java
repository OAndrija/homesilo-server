package com.andrija.homesiloserver.invite.dto;

import com.andrija.homesiloserver.invite.Invite;
import com.andrija.homesiloserver.invite.InviteStatus;
import com.andrija.homesiloserver.user.UserRole;

import java.time.LocalDateTime;
import java.util.UUID;

/** An invite as admins see it in the list. The link isn't here: it's shown only when made. */
public record InviteResponse(
        UUID id,
        UserRole role,
        String note,
        InviteStatus status,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        String usedBy,
        LocalDateTime usedAt
) {
    public static InviteResponse from(Invite invite, LocalDateTime now) {
        return new InviteResponse(invite.getId(), invite.getRole(), invite.getNote(), invite.status(now),
                invite.getCreatedBy() != null ? invite.getCreatedBy().getUsername() : null,
                invite.getCreatedAt(), invite.getExpiresAt(),
                invite.getUsedBy() != null ? invite.getUsedBy().getUsername() : null,
                invite.getUsedAt());
    }
}

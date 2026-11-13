package com.andrija.homesiloserver.invite.dto;

import java.time.LocalDateTime;

/** What someone opening an invite link sees before signing up. */
public record InvitePreviewResponse(String invitedBy, LocalDateTime expiresAt) {}

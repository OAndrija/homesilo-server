package com.andrija.homesiloserver.invite.dto;

/**
 * A new invite and its secret. The app turns the secret into the link to send;
 * this is the only time it's available.
 */
public record CreatedInviteResponse(InviteResponse invite, String token) {}

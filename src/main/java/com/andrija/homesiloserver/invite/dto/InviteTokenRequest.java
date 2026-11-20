package com.andrija.homesiloserver.invite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An invite link's secret. Sent in a body rather than the URL, and kept after
 * the # in the link itself, so it doesn't end up in server logs or Referer headers.
 */
public record InviteTokenRequest(
        @NotBlank(message = "That invite link isn't complete")
        @Size(max = 128, message = "That invite link isn't valid")
        String token
) {}

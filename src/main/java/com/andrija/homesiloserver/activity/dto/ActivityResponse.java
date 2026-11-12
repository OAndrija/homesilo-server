package com.andrija.homesiloserver.activity.dto;

import com.andrija.homesiloserver.activity.ActivityAction;
import com.andrija.homesiloserver.activity.ActivityCategory;
import com.andrija.homesiloserver.activity.ActivityEntry;
import com.andrija.homesiloserver.activity.TargetType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One activity entry. {@code actorUsername} is null when HomeSilo did it, or for a
 * sign-in attempt; see {@link ActivityAction} for what old and new values hold.
 */
public record ActivityResponse(
        UUID id,
        LocalDateTime occurredAt,
        UUID actorId,
        String actorUsername,
        ActivityAction action,
        ActivityCategory category,
        TargetType targetType,
        UUID targetId,
        String targetName,
        String oldValue,
        String newValue,
        String ipAddress
) {
    public static ActivityResponse from(ActivityEntry entry) {
        return new ActivityResponse(
                entry.getId(),
                entry.getOccurredAt(),
                entry.getActorId(),
                entry.getActorUsername(),
                entry.getAction(),
                entry.getAction().category(),
                entry.getTargetType(),
                entry.getTargetId(),
                entry.getTargetName(),
                entry.getOldValue(),
                entry.getNewValue(),
                entry.getIpAddress());
    }
}

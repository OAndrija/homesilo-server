package com.andrija.homesiloserver.activity;

import java.time.LocalDateTime;
import java.util.UUID;

/** Published while the change happens; written to the log only once it has committed. */
public record ActivityEvent(
        LocalDateTime occurredAt,
        UUID actorId,
        String actorUsername,
        ActivityAction action,
        TargetType targetType,
        UUID targetId,
        String targetName,
        String oldValue,
        String newValue,
        String ipAddress
) {}

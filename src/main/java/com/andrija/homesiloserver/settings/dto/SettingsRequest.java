package com.andrija.homesiloserver.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Settings to change; the ones left out (null) stay as they are. */
public record SettingsRequest(
        Boolean registrationOpen,
        @Min(value = 0, message = "Storage can't be negative")
        @Max(value = 1L << 50, message = "That's more storage than HomeSilo can count")
        Long defaultQuotaBytes,
        @Min(value = 0, message = "Use 0 to keep trash forever")
        @Max(value = 3650, message = "Trash can be kept at most 10 years")
        Integer trashRetentionDays,
        @Min(value = 0, message = "Use 0 to keep the activity log forever")
        @Max(value = 3650, message = "The activity log can be kept at most 10 years")
        Integer activityRetentionDays
) {}

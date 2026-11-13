package com.andrija.homesiloserver.user.preferences.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Settings to change, by name. Only the ones named change; a null value puts
 * that setting back to its default.
 */
public record PreferencesRequest(
        @NotNull(message = "Send the settings to change")
        @Size(min = 1, max = 64, message = "Change between 1 and 64 settings at a time")
        Map<String, String> values
) {
}

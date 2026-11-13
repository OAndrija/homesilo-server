package com.andrija.homesiloserver.user.preferences.dto;

import java.time.LocalDateTime;
import java.util.Map;

/** Every setting someone has changed; anything missing has its default. */
public record PreferencesResponse(Map<String, String> values, LocalDateTime updatedAt) {

    public static PreferencesResponse defaults() {
        return new PreferencesResponse(Map.of(), null);
    }
}

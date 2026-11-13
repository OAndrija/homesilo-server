package com.andrija.homesiloserver.user.preferences;

import com.andrija.homesiloserver.common.exception.BadRequestException;
import com.andrija.homesiloserver.user.preferences.dto.PreferencesRequest;
import com.andrija.homesiloserver.user.preferences.dto.PreferencesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserPreferencesService {

    private final UserPreferencesRepository repository;

    @Transactional(readOnly = true)
    public PreferencesResponse get(UUID userId) {
        return repository.findById(userId).map(UserPreferencesService::toResponse)
                .orElseGet(PreferencesResponse::defaults);
    }

    /** Changes the named settings and leaves the rest as they are. */
    public PreferencesResponse update(UUID userId, PreferencesRequest request) {
        request.values().forEach(UserPreferencesService::check);

        UserPreferences preferences = repository.findById(userId).orElseGet(() -> new UserPreferences(userId));
        Map<String, String> values = preferences.getValues();
        request.values().forEach((key, value) -> {
            if (value == null) values.remove(key);
            else values.put(key, value);
        });
        if (values.size() > PreferenceKeys.MAX_VALUES) {
            throw new BadRequestException("An account can keep at most " + PreferenceKeys.MAX_VALUES + " settings");
        }
        if (values.isEmpty()) {
            // Everything is back to its default: nothing to keep.
            if (preferences.getUpdatedAt() != null) repository.delete(preferences);
            return PreferencesResponse.defaults();
        }
        preferences.setUpdatedAt(LocalDateTime.now());
        return toResponse(repository.save(preferences));
    }

    private static void check(String key, String value) {
        PreferenceKeys.Rule rule = PreferenceKeys.ruleFor(key)
                .orElseThrow(() -> new BadRequestException("There is no setting called \"" + abbreviate(key) + "\""));
        if (value != null && !rule.accepts(value)) {
            throw new BadRequestException(rule.message());
        }
    }

    private static String abbreviate(String key) {
        return key.length() > 48 ? key.substring(0, 48) + "…" : key;
    }

    private static PreferencesResponse toResponse(UserPreferences preferences) {
        return new PreferencesResponse(new TreeMap<>(preferences.getValues()), preferences.getUpdatedAt());
    }
}

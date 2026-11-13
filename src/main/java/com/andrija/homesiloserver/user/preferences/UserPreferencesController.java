package com.andrija.homesiloserver.user.preferences;

import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.user.preferences.dto.PreferencesRequest;
import com.andrija.homesiloserver.user.preferences.dto.PreferencesResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in person's settings: appearance, and how their lists are viewed and sorted. */
@RestController
@RequestMapping("/api/v1/users/me/preferences")
@RequiredArgsConstructor
public class UserPreferencesController {

    private final UserPreferencesService service;

    @GetMapping
    public PreferencesResponse get(@AuthenticationPrincipal ServerUserDetails principal) {
        return service.get(principal.getId());
    }

    @PatchMapping
    public PreferencesResponse update(
            @Valid @RequestBody PreferencesRequest request,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return service.update(principal.getId(), request);
    }
}

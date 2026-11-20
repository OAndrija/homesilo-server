package com.andrija.homesiloserver.settings;

import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.settings.dto.SettingsRequest;
import com.andrija.homesiloserver.settings.dto.SettingsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Server settings, for admins (by the /api/v1/admin/** rule). */
@RestController
@RequestMapping("/api/v1/admin/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final ServerSettings settings;

    @GetMapping
    public SettingsResponse get() {
        return settings.get();
    }

    @PatchMapping
    public SettingsResponse update(@Valid @RequestBody SettingsRequest request,
                                   @AuthenticationPrincipal ServerUserDetails principal) {
        return settings.update(request, principal.getId());
    }
}

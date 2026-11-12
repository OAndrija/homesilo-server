package com.andrija.homesiloserver.activity;

import com.andrija.homesiloserver.activity.dto.ActivityResponse;
import com.andrija.homesiloserver.common.web.PageResponse;
import com.andrija.homesiloserver.security.ServerUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /** The server's activity, newest first. Admins only. */
    @GetMapping("/api/v1/admin/activity")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<ActivityResponse> serverActivity(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) ActivityCategory category,
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return activityService.search(query, category, userId, page, size);
    }

    /** The signed-in person's account history (sign-ins, password and admin changes). */
    @GetMapping("/api/v1/users/me/activity")
    public PageResponse<ActivityResponse> myActivity(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return activityService.forAccount(principal.getId(), page, size);
    }
}

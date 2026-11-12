package com.andrija.homesiloserver.dashboard;

import com.andrija.homesiloserver.dashboard.dto.DashboardStatsResponse;
import com.andrija.homesiloserver.dashboard.layout.DashboardLayoutService;
import com.andrija.homesiloserver.dashboard.layout.dto.DashboardLayoutRequest;
import com.andrija.homesiloserver.dashboard.layout.dto.DashboardLayoutResponse;
import jakarta.validation.Valid;
import com.andrija.homesiloserver.security.ServerUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final DashboardLayoutService layoutService;

    @GetMapping("/stats")
    public DashboardStatsResponse getStats(@AuthenticationPrincipal ServerUserDetails principal) {
        return dashboardService.getStats(principal.getId());
    }

    /** Which tiles the dashboard shows, in order and with their sizes. */
    @GetMapping("/layout")
    public DashboardLayoutResponse getLayout(@AuthenticationPrincipal ServerUserDetails principal) {
        return layoutService.get(principal.getId());
    }

    @PutMapping("/layout")
    public DashboardLayoutResponse saveLayout(
            @Valid @RequestBody DashboardLayoutRequest request,
            @AuthenticationPrincipal ServerUserDetails principal
    ) {
        return layoutService.save(principal.getId(), request);
    }

    /** Goes back to the default dashboard. */
    @DeleteMapping("/layout")
    public ResponseEntity<Void> resetLayout(@AuthenticationPrincipal ServerUserDetails principal) {
        layoutService.reset(principal.getId());
        return ResponseEntity.noContent().build();
    }
}

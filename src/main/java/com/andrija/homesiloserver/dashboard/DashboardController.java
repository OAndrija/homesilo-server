package com.andrija.homesiloserver.dashboard;

import com.andrija.homesiloserver.dashboard.dto.DashboardStatsResponse;
import com.andrija.homesiloserver.security.ServerUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public DashboardStatsResponse getStats(@AuthenticationPrincipal ServerUserDetails principal) {
        return dashboardService.getStats(principal.getId());
    }
}

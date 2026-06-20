package com.andrija.homesiloserver.controller;

import com.andrija.homesiloserver.dto.DashboardStatsResponse;
import com.andrija.homesiloserver.security.ServerUserDetails;
import com.andrija.homesiloserver.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final FileService fileService;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getStats(
            @AuthenticationPrincipal ServerUserDetails userDetails
    ) {
        return ResponseEntity.ok(fileService.getDashboardStats(userDetails.getId()));
    }
}

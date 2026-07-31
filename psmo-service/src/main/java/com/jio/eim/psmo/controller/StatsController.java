package com.jio.eim.psmo.controller;

import com.jio.eim.psmo.dto.ApiResponse;
import com.jio.eim.psmo.dto.DashboardResponse;
import com.jio.eim.psmo.service.DashboardService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/psmo")
@Validated
public class StatsController {

    private final DashboardService dashboardService;

    public StatsController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Aggregated psmo tiles for the portal dashboard. {@code days} sizes the time-series/donut
     * windows (the "today", "24h polling" and all-time-downloads figures ignore it).
     * e.g. {@code GET /api/psmo/dashboard?days=14}.
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ENGINEER','READ_ONLY','BSS_SYSTEM')")
    public ApiResponse<DashboardResponse> dashboard(
            @RequestParam(defaultValue = "14") @Min(1) @Max(90) int days) {
        return ApiResponse.ok("Dashboard retrieved", dashboardService.getDashboard(days));
    }
}
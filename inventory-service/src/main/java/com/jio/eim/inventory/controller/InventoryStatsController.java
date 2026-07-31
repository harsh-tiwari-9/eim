package com.jio.eim.inventory.controller;

import com.jio.eim.inventory.dto.ApiResponse;
import com.jio.eim.inventory.dto.InventorySummaryResponse;
import com.jio.eim.inventory.service.InventoryStatsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@Validated
public class InventoryStatsController {

    private final InventoryStatsService statsService;

    public InventoryStatsController(InventoryStatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * Inventory-owned dashboard tiles: total SIMs + daily onboarding trend. {@code days} sizes the
     * trend window. e.g. {@code GET /api/inventory/stats/summary?days=14}.
     */
    @GetMapping("/stats/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ENGINEER','READ_ONLY','BSS_SYSTEM')")
    public ApiResponse<InventorySummaryResponse> summary(
            @RequestParam(defaultValue = "14") @Min(1) @Max(90) int days) {
        return ApiResponse.ok("Inventory summary retrieved", statsService.getSummary(days));
    }
}
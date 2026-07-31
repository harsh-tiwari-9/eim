package com.jio.eim.inventory.dto;

import java.util.List;

/** Inventory-owned dashboard tiles: total SIMs and the daily onboarding trend. */
public record InventorySummaryResponse(
        long totalSims,
        List<OnboardingPoint> onboardingTrend) {

    /** New eUICCs registered on a given calendar day (business timezone). */
    public record OnboardingPoint(String date, long count) {}
}
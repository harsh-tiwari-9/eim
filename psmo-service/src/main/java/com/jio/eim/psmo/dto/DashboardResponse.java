package com.jio.eim.psmo.dto;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated figures for the portal dashboard page (psmo-owned tiles). The inventory-owned tiles
 * (total SIMs, onboarding trend) come from a separate inventory-service endpoint.
 */
public record DashboardResponse(
        long simsPolling24h,
        long operationsToday,
        SuccessRate successRate,
        long downloadsCompleted,
        List<TypeCount> typeSplit,
        List<OutcomePoint> outcomeOverTime,
        List<RecentOperation> recentOperations) {

    /** EXECUTED vs FAILED over the rate window; {@code ratePct} = executed / (executed+failed) * 100. */
    public record SuccessRate(long executed, long failed, double ratePct, int windowDays) {}

    /** One slice of the operation-type donut. */
    public record TypeCount(String type, long count) {}

    /** One day in the outcome stacked-area chart. */
    public record OutcomePoint(String date, long success, long failed, long pending) {}

    /** One row of the live recent-operations feed. */
    public record RecentOperation(
            Long id,
            String eid,
            String type,
            String status,
            String requestedBy,
            Instant createdAt,
            Instant completedAt) {}
}
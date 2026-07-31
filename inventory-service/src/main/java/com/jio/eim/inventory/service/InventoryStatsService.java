package com.jio.eim.inventory.service;

import com.jio.eim.inventory.dto.InventorySummaryResponse;
import com.jio.eim.inventory.dto.InventorySummaryResponse.OnboardingPoint;
import com.jio.eim.inventory.repository.InventoryDeviceRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the inventory-owned dashboard figures. See {@link InventorySummaryResponse}. */
@Service
public class InventoryStatsService {

    private final InventoryDeviceRepository deviceRepository;
    private final ZoneId zone;

    public InventoryStatsService(
            InventoryDeviceRepository deviceRepository,
            @Value("${eim.analytics.zone:Asia/Kolkata}") String zoneId) {
        this.deviceRepository = deviceRepository;
        this.zone = ZoneId.of(zoneId);
    }

    @Transactional(readOnly = true)
    public InventorySummaryResponse getSummary(int days) {
        Instant since = LocalDate.now(zone).minusDays(days - 1L).atStartOfDay(zone).toInstant();

        List<OnboardingPoint> trend = new ArrayList<>();
        for (Object[] row : deviceRepository.onboardingTrend(since, zone.getId())) {
            trend.add(new OnboardingPoint((String) row[0], ((Number) row[1]).longValue()));
        }
        return new InventorySummaryResponse(deviceRepository.countActive(), trend);
    }
}
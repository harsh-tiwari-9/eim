package com.jio.eim.psmo.service;

import com.jio.eim.psmo.dto.DashboardResponse;
import com.jio.eim.psmo.dto.DashboardResponse.OutcomePoint;
import com.jio.eim.psmo.dto.DashboardResponse.RecentOperation;
import com.jio.eim.psmo.dto.DashboardResponse.SuccessRate;
import com.jio.eim.psmo.dto.DashboardResponse.TypeCount;
import com.jio.eim.psmo.entity.Operation;
import com.jio.eim.psmo.repository.DownloadSessionRepository;
import com.jio.eim.psmo.repository.OperationRepository;
import com.jio.eim.psmo.repository.PollHistoryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the psmo-owned dashboard figures. See {@link DashboardResponse}. */
@Service
public class DashboardService {

    /** Rolling window (days) for the success-rate tile — steadier than a single calendar day. */
    private static final int SUCCESS_RATE_WINDOW_DAYS = 7;
    private static final String STATUS_EXECUTED = "EXECUTED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String DOWNLOAD_COMPLETED = "COMPLETED";

    private final OperationRepository operationRepository;
    private final DownloadSessionRepository downloadSessionRepository;
    private final PollHistoryRepository pollHistoryRepository;
    private final ZoneId zone;

    public DashboardService(
            OperationRepository operationRepository,
            DownloadSessionRepository downloadSessionRepository,
            PollHistoryRepository pollHistoryRepository,
            @Value("${eim.analytics.zone:Asia/Kolkata}") String zoneId) {
        this.operationRepository = operationRepository;
        this.downloadSessionRepository = downloadSessionRepository;
        this.pollHistoryRepository = pollHistoryRepository;
        this.zone = ZoneId.of(zoneId);
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(int days) {
        Instant now = Instant.now();
        Instant polling24hSince = now.minus(24, ChronoUnit.HOURS);
        Instant startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant rateSince = LocalDate.now(zone).minusDays(SUCCESS_RATE_WINDOW_DAYS - 1L)
                .atStartOfDay(zone).toInstant();
        // Chart windows cover `days` whole calendar days, oldest day starting at local midnight.
        Instant chartSince = LocalDate.now(zone).minusDays(days - 1L).atStartOfDay(zone).toInstant();

        long simsPolling24h = pollHistoryRepository.countDistinctEidPolledSince(polling24hSince);
        long operationsToday = operationRepository.countByCreatedAtGreaterThanEqual(startOfToday);
        long downloadsCompleted = downloadSessionRepository.countByStatus(DOWNLOAD_COMPLETED);

        return new DashboardResponse(
                simsPolling24h,
                operationsToday,
                successRate(rateSince),
                downloadsCompleted,
                typeSplit(chartSince),
                outcomeOverTime(chartSince),
                recentOperations());
    }

    private SuccessRate successRate(Instant since) {
        long executed = operationRepository.countByStatusAndCreatedAtGreaterThanEqual(STATUS_EXECUTED, since);
        long failed = operationRepository.countByStatusAndCreatedAtGreaterThanEqual(STATUS_FAILED, since);
        long terminal = executed + failed;
        double ratePct = terminal == 0 ? 0.0
                : BigDecimal.valueOf(executed * 100.0 / terminal)
                        .setScale(1, RoundingMode.HALF_UP).doubleValue();
        return new SuccessRate(executed, failed, ratePct, SUCCESS_RATE_WINDOW_DAYS);
    }

    private List<TypeCount> typeSplit(Instant since) {
        List<TypeCount> out = new ArrayList<>();
        for (Object[] row : operationRepository.typeCountsSince(since)) {
            out.add(new TypeCount((String) row[0], ((Number) row[1]).longValue()));
        }
        return out;
    }

    private List<OutcomePoint> outcomeOverTime(Instant since) {
        // Pivot the [day, bucket, count] rows into one point per day.
        Map<String, long[]> byDay = new LinkedHashMap<>(); // day -> [success, failed, pending]
        for (Object[] row : operationRepository.outcomeOverTime(since, zone.getId())) {
            String day = (String) row[0];
            String bucket = (String) row[1];
            long count = ((Number) row[2]).longValue();
            long[] buckets = byDay.computeIfAbsent(day, k -> new long[3]);
            switch (bucket) {
                case "success" -> buckets[0] += count;
                case "failed" -> buckets[1] += count;
                default -> buckets[2] += count;
            }
        }
        List<OutcomePoint> out = new ArrayList<>();
        for (Map.Entry<String, long[]> e : byDay.entrySet()) {
            long[] b = e.getValue();
            out.add(new OutcomePoint(e.getKey(), b[0], b[1], b[2]));
        }
        return out;
    }

    private List<RecentOperation> recentOperations() {
        List<RecentOperation> out = new ArrayList<>();
        for (Operation o : operationRepository.findTop15ByOrderByCreatedAtDesc()) {
            out.add(new RecentOperation(o.getId(), o.getEid(), o.getType(), o.getStatus(),
                    o.getRequestedBy(), o.getCreatedAt(), o.getCompletedAt()));
        }
        return out;
    }
}
package com.jio.eim.inventory.repository;

import com.jio.eim.inventory.entity.InventoryDevice;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryDeviceRepository extends JpaRepository<InventoryDevice, String> {

    // ---- Dashboard analytics -------------------------------------------------------------------

    /** "Total SIMs" — registered eUICCs, excluding deleted ones (mirrors the {@code search} filter). */
    @Query("SELECT COUNT(d) FROM InventoryDevice d WHERE d.status <> 'DELETED'")
    long countActive();

    /**
     * Daily onboarding counts for the trend line. {@code registered_at} is a {@code TIMESTAMP} holding
     * UTC wall time, re-interpreted as UTC then shifted to {@code :zone} before truncating to a day.
     * Rows: [yyyy-MM-dd, count].
     */
    @Query(value = "SELECT to_char(date_trunc('day', (registered_at AT TIME ZONE 'UTC') AT TIME ZONE :zone), "
            + "'YYYY-MM-DD') AS d, COUNT(*) AS cnt "
            + "FROM inventory.devices WHERE registered_at >= :since AND status <> 'DELETED' "
            + "GROUP BY d ORDER BY d", nativeQuery = true)
    List<Object[]> onboardingTrend(@Param("since") Instant since, @Param("zone") String zone);

    @Query("""
            SELECT d FROM InventoryDevice d
            WHERE (:ownerId IS NULL OR d.ownerId = :ownerId)
              AND (:status IS NULL OR d.status = :status)
              AND (:eid IS NULL OR d.eid = :eid)
              AND (:search IS NULL OR LOWER(d.eid) LIKE LOWER(CONCAT(CAST(:search AS string), '%')))
              AND d.status <> 'DELETED'
            """)
    Page<InventoryDevice> search(
            @Param("ownerId") String ownerId,
            @Param("status") String status,
            @Param("eid") String eid,
            @Param("search") String search,
            Pageable pageable);
}
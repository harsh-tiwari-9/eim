package com.jio.eim.psmo.repository;

import com.jio.eim.psmo.entity.Operation;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationRepository extends JpaRepository<Operation, Long> {

    // ---- Dashboard analytics -------------------------------------------------------------------

    /** "Operations today" — pass the start-of-day instant in the business timezone. */
    long countByCreatedAtGreaterThanEqual(Instant since);

    /** "Operation success rate" — call for EXECUTED and FAILED over the rate window. */
    long countByStatusAndCreatedAtGreaterThanEqual(String status, Instant since);

    /** Operation-type split for the donut: rows of [type, count] over the window. */
    @Query("SELECT o.type, COUNT(o) FROM Operation o WHERE o.createdAt >= :since GROUP BY o.type")
    List<Object[]> typeCountsSince(@Param("since") Instant since);

    /**
     * Operations-by-outcome per day for the stacked area chart. Buckets status into
     * success/failed/pending and groups by calendar day in the business timezone. {@code created_at}
     * is a {@code TIMESTAMP} holding UTC wall time, so it is re-interpreted as UTC then shifted to
     * {@code :zone} before truncating. Rows: [yyyy-MM-dd, bucket, count].
     */
    @Query(value = "SELECT to_char(date_trunc('day', (created_at AT TIME ZONE 'UTC') AT TIME ZONE :zone), "
            + "'YYYY-MM-DD') AS d, "
            + "CASE WHEN status = 'EXECUTED' THEN 'success' "
            + "     WHEN status = 'FAILED'   THEN 'failed' "
            + "     ELSE 'pending' END AS bucket, "
            + "COUNT(*) AS cnt "
            + "FROM psmo.operations WHERE created_at >= :since "
            + "GROUP BY d, bucket ORDER BY d", nativeQuery = true)
    List<Object[]> outcomeOverTime(@Param("since") Instant since, @Param("zone") String zone);

    /** Newest operations for the live recent-operations feed. */
    List<Operation> findTop15ByOrderByCreatedAtDesc();

    List<Operation> findByEidOrderByCreatedAtDesc(String eid);

    /** Batch fetch by id — for the UI "refresh statuses of the rows on screen" call. */
    List<Operation> findByIdIn(Collection<Long> ids);

    /** Most recent successful operation of a type for a device — used to fetch the latest AUDIT snapshot. */
    Optional<Operation> findFirstByEidAndTypeAndStatusOrderByCompletedAtDesc(
            String eid, String type, String status);

    /**
     * Paginated operation history for the ops/logs page. Each filter is optional — a null value
     * matches everything for that field.
     */
    @Query("SELECT o FROM Operation o WHERE (:eid IS NULL OR o.eid = :eid) "
            + "AND (:type IS NULL OR o.type = :type) "
            + "AND (:status IS NULL OR o.status = :status)")
    Page<Operation> search(@Param("eid") String eid,
                           @Param("type") String type,
                           @Param("status") String status,
                           Pageable pageable);
}
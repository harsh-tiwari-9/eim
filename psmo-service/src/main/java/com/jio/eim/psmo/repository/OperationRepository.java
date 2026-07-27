package com.jio.eim.psmo.repository;

import com.jio.eim.psmo.entity.Operation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationRepository extends JpaRepository<Operation, Long> {
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
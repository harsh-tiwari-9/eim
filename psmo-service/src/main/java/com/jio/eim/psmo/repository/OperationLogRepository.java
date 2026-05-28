package com.jio.eim.psmo.repository;

import com.jio.eim.psmo.dto.OperationLogResponse;
import com.jio.eim.psmo.entity.OperationLog;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    List<OperationLog> findByOperationIdOrderByTsAsc(Long operationId);

    @Query("""
            SELECT new com.jio.eim.psmo.dto.OperationLogResponse(
                l.id, l.operationId, l.eventType, l.actor, l.details, l.ts,
                o.eid, o.type, o.targetIccid, o.status)
            FROM OperationLog l
            JOIN Operation o ON o.id = l.operationId
            WHERE (:operationId IS NULL OR l.operationId = :operationId)
              AND (:eventType   IS NULL OR l.eventType   = :eventType)
              AND (:actor       IS NULL OR l.actor       = :actor)
              AND (cast(:fromTs as timestamp) IS NULL OR l.ts >= :fromTs)
              AND (cast(:toTs   as timestamp) IS NULL OR l.ts <= :toTs)
            """)
    Page<OperationLogResponse> searchWithContext(
            @Param("operationId") Long operationId,
            @Param("eventType") String eventType,
            @Param("actor") String actor,
            @Param("fromTs") Instant fromTs,
            @Param("toTs") Instant toTs,
            Pageable pageable);

    @Query("""
            SELECT new com.jio.eim.psmo.dto.OperationLogResponse(
                l.id, l.operationId, l.eventType, l.actor, l.details, l.ts,
                o.eid, o.type, o.targetIccid, o.status)
            FROM OperationLog l
            JOIN Operation o ON o.id = l.operationId
            WHERE l.operationId = :operationId
            ORDER BY l.ts ASC
            """)
    List<OperationLogResponse> findEnrichedByOperationId(@Param("operationId") Long operationId);
}
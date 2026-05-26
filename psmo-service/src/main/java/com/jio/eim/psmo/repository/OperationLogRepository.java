package com.jio.eim.psmo.repository;

import com.jio.eim.psmo.entity.OperationLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    List<OperationLog> findByOperationIdOrderByTsAsc(Long operationId);
}
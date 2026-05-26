package com.jio.eim.psmo.repository;

import com.jio.eim.psmo.entity.Operation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationRepository extends JpaRepository<Operation, Long> {
    List<Operation> findByEidOrderByCreatedAtDesc(String eid);
}
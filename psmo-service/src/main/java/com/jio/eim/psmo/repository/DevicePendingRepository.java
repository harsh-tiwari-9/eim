package com.jio.eim.psmo.repository;

import com.jio.eim.psmo.entity.DevicePending;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DevicePendingRepository extends JpaRepository<DevicePending, String> {

    List<DevicePending> findByEidOrderByQueuedAtAsc(String eid);

    Optional<DevicePending> findFirstByEidOrderByQueuedAtAsc(String eid);

    void deleteByOperationId(Long operationId);
}

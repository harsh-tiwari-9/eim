package com.jio.eim.psmo.repository;

import com.jio.eim.psmo.entity.EimPackageCounter;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EimPackageCounterRepository extends JpaRepository<EimPackageCounter, String> {

    /**
     * Locks the counter row for this eUICC so the read-increment-write stays monotonic when
     * multiple packages are signed for the same eUICC concurrently.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EimPackageCounter c where c.eid = :eid")
    Optional<EimPackageCounter> findForUpdate(@Param("eid") String eid);
}
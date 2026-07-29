package com.jio.eim.psmo.repository;

import com.jio.eim.psmo.entity.PollHistory;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PollHistoryRepository extends JpaRepository<PollHistory, Long> {

    /** Distinct devices that polled since {@code since} — the dashboard "SIMs polling (24h)" tile. */
    @Query("SELECT COUNT(DISTINCT p.eid) FROM PollHistory p WHERE p.polledAt >= :since")
    long countDistinctEidPolledSince(@Param("since") Instant since);

    /** Newest-first poll timeline for a single device — for a future per-device poll-history API. */
    List<PollHistory> findByEidOrderByPolledAtDesc(String eid, Pageable pageable);
}
package com.jio.eim.inventory.repository;

import com.jio.eim.inventory.entity.IngestRow;
import com.jio.eim.inventory.ingest.IngestRowStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestRowRepository extends JpaRepository<IngestRow, Long> {

    List<IngestRow> findByJobIdOrderByRowNumberAsc(Long jobId);

    long countByJobIdAndStatusNotIn(Long jobId, Collection<IngestRowStatus> terminalStatuses);

    long countByJobIdAndStatus(Long jobId, IngestRowStatus status);
}
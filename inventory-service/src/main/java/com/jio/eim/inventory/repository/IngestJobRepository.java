package com.jio.eim.inventory.repository;

import com.jio.eim.inventory.entity.IngestJob;
import com.jio.eim.inventory.ingest.IngestJobStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IngestJobRepository extends JpaRepository<IngestJob, Long> {

    List<IngestJob> findByStatusOrderByCreatedAtAsc(IngestJobStatus status);

    @Query(value = """
            SELECT * FROM inventory.ingest_jobs
            WHERE status = :status
            ORDER BY created_at ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<IngestJob> findNextJobForProcessing(@Param("status") String status);

    @Query("""
            SELECT j FROM IngestJob j
            WHERE (:status IS NULL OR j.status = :status)
              AND (:uploadedBy IS NULL OR j.uploadedBy = :uploadedBy)
            """)
    Page<IngestJob> search(
            @Param("status") IngestJobStatus status,
            @Param("uploadedBy") String uploadedBy,
            Pageable pageable);
}

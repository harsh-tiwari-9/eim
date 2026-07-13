package com.jio.eim.psmo.repository;

import com.jio.eim.psmo.entity.DownloadSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DownloadSessionRepository extends JpaRepository<DownloadSession, String> {

    /** The eUICC echoes the transactionId bytes; the stored key keeps the SM-DP+'s original case. */
    Optional<DownloadSession> findByTransactionIdIgnoreCase(String transactionId);
}
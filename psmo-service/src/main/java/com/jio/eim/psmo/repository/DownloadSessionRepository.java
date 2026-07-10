package com.jio.eim.psmo.repository;

import com.jio.eim.psmo.entity.DownloadSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DownloadSessionRepository extends JpaRepository<DownloadSession, String> {
}
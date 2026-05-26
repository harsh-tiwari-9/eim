package com.jio.eim.inventory.entity;

import com.jio.eim.inventory.ingest.IngestJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(schema = "inventory", name = "ingest_jobs")
@Getter
@Setter
public class IngestJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", length = 500)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IngestJobStatus status;

    @Column(name = "total_records")
    private long totalRecords;

    @Column(name = "processed_records")
    private long processedRecords;

    @Column(name = "failed_records")
    private long failedRecords;

    @Column(name = "uploaded_by", length = 255)
    private String uploadedBy;

    @Column(name = "input_file_path", length = 500)
    private String inputFilePath;

    @Column(name = "output_file_path", length = 500)
    private String outputFilePath;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = IngestJobStatus.UPLOADED;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
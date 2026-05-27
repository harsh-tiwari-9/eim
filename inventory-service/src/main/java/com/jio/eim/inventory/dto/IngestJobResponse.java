package com.jio.eim.inventory.dto;

import com.jio.eim.inventory.ingest.IngestJobStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IngestJobResponse {

    private Long jobId;
    private IngestJobStatus status;
    private String fileName;
    private String uploadedBy;
    private String inputFilePath;
    private String outputFilePath;
    private String remarks;
    private long totalRecords;
    private long processedRecords;
    private long failedRecords;
    private Instant createdAt;
    private Instant completedAt;
}
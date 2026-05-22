package com.jio.eim.inventory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jio.eim.inventory.entity.IngestJob;
import com.jio.eim.inventory.entity.IngestRow;
import com.jio.eim.inventory.ingest.IngestJobStatus;
import com.jio.eim.inventory.ingest.IngestRowStatus;
import com.jio.eim.inventory.repository.IngestJobRepository;
import com.jio.eim.inventory.repository.IngestRowRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestJobCompletionService {

    private final IngestJobRepository jobRepository;
    private final IngestRowRepository rowRepository;
    private final BlobStorageService blobStorageService;
    private final ObjectMapper objectMapper;

    public IngestJobCompletionService(
            IngestJobRepository jobRepository,
            IngestRowRepository rowRepository,
            BlobStorageService blobStorageService,
            ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.blobStorageService = blobStorageService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${eim.ingest.poller.fixed-delay-ms:5000}")
    @Transactional
    public void completeFinishedJobs() {
        List<IngestJob> processingJobs = jobRepository.findByStatusOrderByCreatedAtAsc(IngestJobStatus.PROCESSING);
        for (IngestJob job : processingJobs) {
            if (job.getTotalRecords() == 0) {
                continue;
            }
            long pending = rowRepository.countByJobIdAndStatus(job.getId(), IngestRowStatus.VALIDATED);
            if (pending > 0) {
                continue;
            }
            try {
                finalizeJob(job);
            } catch (Exception e) {
                job.setStatus(IngestJobStatus.FAILED);
                job.setRemarks("Completion failed: " + e.getMessage());
                job.setCompletedAt(Instant.now());
                jobRepository.save(job);
            }
        }
    }

    private void finalizeJob(IngestJob job) throws Exception {
        List<IngestRow> rows = rowRepository.findByJobIdOrderByRowNumberAsc(job.getId());
        List<Map<String, Object>> report = new ArrayList<>();
        for (IngestRow row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rowNumber", row.getRowNumber());
            entry.put("eid", row.getEid());
            entry.put("status", row.getStatus().name());
            entry.put("remarks", row.getRemarks());
            report.add(entry);
        }

        byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
        String outputKey = blobStorageService.outputKey(job.getId());
        blobStorageService.upload(
                outputKey,
                new ByteArrayInputStream(bytes),
                bytes.length,
                "application/json");

        job.setOutputFilePath(outputKey);
        job.setStatus(IngestJobStatus.COMPLETED);
        job.setCompletedAt(Instant.now());
        job.setRemarks("Processed " + job.getProcessedRecords() + " ok, " + job.getFailedRecords() + " failed");
        jobRepository.save(job);
    }
}

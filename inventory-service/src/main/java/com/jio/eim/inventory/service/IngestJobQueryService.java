package com.jio.eim.inventory.service;

import com.jio.eim.inventory.dto.IngestJobResponse;
import com.jio.eim.inventory.entity.IngestJob;
import com.jio.eim.inventory.ingest.IngestJobStatus;
import com.jio.eim.inventory.repository.IngestJobRepository;
import io.minio.GetObjectResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import com.jio.eim.inventory.dto.PagedResponse;

@Service
public class IngestJobQueryService {

    private final IngestJobRepository jobRepository;
    private final BlobStorageService blobStorageService;

    public IngestJobQueryService(IngestJobRepository jobRepository, BlobStorageService blobStorageService) {
        this.jobRepository = jobRepository;
        this.blobStorageService = blobStorageService;
    }

    @Transactional(readOnly = true)
    public PagedResponse<IngestJobResponse> listJobs(
            IngestJobStatus status,
            String uploadedBy,
            Pageable pageable) {

        Page<IngestJob> jobsPage = jobRepository.search(status,blankToNull(uploadedBy), pageable);

        List<IngestJobResponse> content = jobsPage.getContent().stream()
                .map(IngestJobMapper::toResponse)
                .toList();

        return PagedResponse.from(jobsPage, content);
    }

    public IngestJobResponse getJob(long jobId) {
        IngestJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        return IngestJobMapper.toResponse(job);
    }

    public ResponseEntity<InputStreamResource> downloadResult(long jobId) {
        IngestJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

        if (job.getStatus() != IngestJobStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Job is not completed yet (status=" + job.getStatus() + ")");
        }

        String outputKey = job.getOutputFilePath();
        if (outputKey == null || outputKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No output file for this job");
        }

        GetObjectResponse object = blobStorageService.download(outputKey);
        String filename = "ingest-job-" + jobId + "-result.json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new InputStreamResource(object));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

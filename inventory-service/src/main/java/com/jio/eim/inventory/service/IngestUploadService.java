package com.jio.eim.inventory.service;

import com.jio.eim.inventory.dto.IngestJobResponse;
import com.jio.eim.inventory.entity.IngestJob;
import com.jio.eim.inventory.ingest.IngestJobStatus;
import com.jio.eim.inventory.repository.IngestJobRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IngestUploadService {
    private final IngestJobRepository jobRepository;
    private final BlobStorageService blobStorageService;

    public IngestUploadService(IngestJobRepository jobRepository, BlobStorageService blobStorageService) {
        this.jobRepository = jobRepository;
        this.blobStorageService = blobStorageService;
    }

    @Transactional
    public IngestJobResponse upload(MultipartFile file, String uploadedByEmail) {
        validateFile(file);

        IngestJob job = new IngestJob();
        job.setFileName(file.getOriginalFilename());
        job.setStatus(IngestJobStatus.UPLOADED);
        job.setUploadedBy(uploadedByEmail);
        job = jobRepository.save(job);

        String objectKey = blobStorageService.inputKey(job.getId());
        try {
            blobStorageService.upload(
                    objectKey,
                    file.getInputStream(),
                    file.getSize(),
                    file.getContentType() != null ? file.getContentType() : "application/json");
        } catch (Exception ex) {
            job.setStatus(IngestJobStatus.FAILED);
            job.setRemarks("MinIO upload failed: " + ex.getMessage());
            jobRepository.save(job);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store upload: " + ex.getMessage());
        }

        job.setInputFilePath(objectKey);
        job = jobRepository.save(job);

        return IngestJobMapper.toResponse(job);
    }

    private void validateFile(MultipartFile file) {
        if(file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }

        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".json")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .json files are allowed");
        }
    }

}
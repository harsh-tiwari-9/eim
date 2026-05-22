package com.jio.eim.inventory.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jio.eim.inventory.dto.InventoryRequest;
import com.jio.eim.inventory.entity.IngestJob;
import com.jio.eim.inventory.entity.IngestRow;
import com.jio.eim.inventory.ingest.CertValidationResult;
import com.jio.eim.inventory.ingest.IngestJobStatus;
import com.jio.eim.inventory.ingest.IngestRecordMessage;
import com.jio.eim.inventory.ingest.IngestRowStatus;
import com.jio.eim.inventory.repository.IngestJobRepository;
import com.jio.eim.inventory.repository.IngestRowRepository;
import io.minio.GetObjectResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IngestFileProcessor {

    private static final Logger log = LoggerFactory.getLogger(IngestFileProcessor.class);

    private final IngestJobRepository jobRepository;
    private final IngestRowRepository rowRepository;
    private final BlobStorageService blobStorageService;
    private final CertificateService certificateService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final String kafkaTopic;

    public IngestFileProcessor(
            IngestJobRepository jobRepository,
            IngestRowRepository rowRepository,
            BlobStorageService blobStorageService,
            CertificateService certificateService,
            ObjectMapper objectMapper,
            Validator validator,
            KafkaTemplate<String, String> kafkaTemplate,
            PlatformTransactionManager transactionManager,
            @Value("${eim.ingest.kafka.topic}") String kafkaTopic) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.blobStorageService = blobStorageService;
        this.certificateService = certificateService;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.kafkaTopic = kafkaTopic;
    }

    @Scheduled(fixedDelayString = "${eim.ingest.poller.fixed-delay-ms:5000}")
    public void pollUploadedJobs() {
        Optional<Long> jobId = claimNextJob();
        if (jobId.isEmpty()) {
            return;
        }
        try {
            processJob(jobId.get());
        } catch (Exception ex) {
            log.error("Ingest job {} failed", jobId.get(), ex);
            markJobFailed(jobId.get(), ex.getMessage());
        }
    }

    private Optional<Long> claimNextJob() {
        return transactionTemplate.execute(status -> {
            Optional<IngestJob> job =
                    jobRepository.findNextJobForProcessing(IngestJobStatus.UPLOADED.name());
            if (job.isEmpty()) {
                return Optional.empty();
            }
            IngestJob claimed = job.get();
            claimed.setStatus(IngestJobStatus.PROCESSING);
            jobRepository.save(claimed);
            return Optional.of(claimed.getId());
        });
    }

    private void processJob(long jobId) throws Exception {
        IngestJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));

        if (job.getInputFilePath() == null || job.getInputFilePath().isBlank()) {
            markJobFailed(jobId, "Missing input file path");
            return;
        }

        long rowNumber = 0;
        JsonFactory jsonFactory = objectMapper.getFactory();

        try (GetObjectResponse object = blobStorageService.download(job.getInputFilePath());
                InputStream inputStream = object;
                var parser = jsonFactory.createParser(inputStream)) {

            JsonToken firstToken = parser.nextToken();
            if (firstToken != JsonToken.START_ARRAY) {
                markJobFailed(jobId, "Input file must be a JSON array of device objects");
                return;
            }

            while (parser.nextToken() != JsonToken.END_ARRAY) {
                rowNumber++;
                InventoryRequest request = objectMapper.readValue(parser, InventoryRequest.class);
                processEntry(job, rowNumber, request);
            }
        }

        job = jobRepository.findById(jobId).orElseThrow();
        job.setTotalRecords(rowNumber);
        jobRepository.save(job);

        if (rowNumber == 0) {
            markJobFailed(jobId, "No device entries found in file");
        }
    }

    private void processEntry(IngestJob job, long rowNumber, InventoryRequest request) throws Exception {
        String payloadJson = objectMapper.writeValueAsString(request);
        String eid = request.getEid();

        Set<ConstraintViolation<InventoryRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String error = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            saveFailedRow(job.getId(), rowNumber, eid, payloadJson, error);
            job.setFailedRecords(job.getFailedRecords() + 1);
            jobRepository.save(job);
            return;
        }

        CertValidationResult certResult =
                certificateService.validateAndExtractSafe(request.getEuiccEumCerts().getFirst());
        if (!certResult.isSuccess()) {
            saveFailedRow(job.getId(), rowNumber, eid, payloadJson, certResult.errorMessage());
            job.setFailedRecords(job.getFailedRecords() + 1);
            jobRepository.save(job);
            return;
        }

        IngestRow row = new IngestRow();
        row.setJobId(job.getId());
        row.setRowNumber(rowNumber);
        row.setEid(eid);
        row.setStatus(IngestRowStatus.VALIDATED);
        row.setPayloadJson(payloadJson);
        row.setRemarks(certResult.certSummary().isChainValid()
                ? "Validated — certificate chain valid"
                : "Validated — certificate chain invalid");
        row = rowRepository.save(row);

        IngestRecordMessage message = new IngestRecordMessage(job.getId(), row.getId(), eid, request);
        String messageJson = objectMapper.writeValueAsString(message);
        kafkaTemplate.send(kafkaTopic, eid, messageJson);
    }

    private void saveFailedRow(long jobId, long rowNumber, String eid, String payloadJson, String remarks) {
        IngestRow row = new IngestRow();
        row.setJobId(jobId);
        row.setRowNumber(rowNumber);
        row.setEid(eid);
        row.setStatus(IngestRowStatus.FAILED);
        row.setPayloadJson(payloadJson);
        row.setRemarks(remarks);
        rowRepository.save(row);
    }

    private void markJobFailed(long jobId, String remarks) {
        transactionTemplate.executeWithoutResult(status -> {
            jobRepository.findById(jobId).ifPresent(job -> {
                job.setStatus(IngestJobStatus.FAILED);
                job.setRemarks(remarks);
                jobRepository.save(job);
            });
        });
    }
}

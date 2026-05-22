package com.jio.eim.inventory.service;

import com.jio.eim.inventory.entity.IngestJob;
import com.jio.eim.inventory.entity.IngestRow;
import com.jio.eim.inventory.ingest.IngestRecordMessage;
import com.jio.eim.inventory.ingest.IngestRowStatus;
import com.jio.eim.inventory.ingest.RegisterResult;
import com.jio.eim.inventory.repository.IngestJobRepository;
import com.jio.eim.inventory.repository.IngestRowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestRegistrationService {

    private final IngestDeviceRegistrationExecutor registrationExecutor;
    private final IngestRowRepository rowRepository;
    private final IngestJobRepository jobRepository;

    public IngestRegistrationService(
            IngestDeviceRegistrationExecutor registrationExecutor,
            IngestRowRepository rowRepository,
            IngestJobRepository jobRepository) {
        this.registrationExecutor = registrationExecutor;
        this.rowRepository = rowRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public void registerFromKafka(IngestRecordMessage message) {
        IngestRow row = rowRepository.findById(message.rowId())
                .orElseThrow(() -> new IllegalStateException("Ingest row not found: " + message.rowId()));
        IngestJob job = jobRepository.findById(message.jobId())
                .orElseThrow(() -> new IllegalStateException("Ingest job not found: " + message.jobId()));

        try {
            RegisterResult result = registrationExecutor.register(message.payload());
            row.setStatus(IngestRowStatus.REGISTERED);
            row.setRemarks(result.remarks());
            job.setProcessedRecords(job.getProcessedRecords() + 1);
        } catch (Exception ex) {
            row.setStatus(IngestRowStatus.FAILED);
            row.setRemarks(truncate(friendlyMessage(ex)));
            job.setFailedRecords(job.getFailedRecords() + 1);
        }

        rowRepository.save(row);
        jobRepository.save(job);
    }

    private String friendlyMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message != null && message.contains("device_profiles_eid_iccid_key")) {
            return "Duplicate profile: this EID and ICCID are already registered";
        }
        if (message != null && message.contains("devices_pkey")) {
            return "Duplicate device: this EID is already registered";
        }
        if (message != null && message.contains("already registered")) {
            return message;
        }
        return message != null ? message : "Registration failed";
    }

    private String truncate(String message) {
        if (message == null) {
            return "Registration failed";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}

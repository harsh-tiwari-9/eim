package com.jio.eim.inventory.service;

import com.jio.eim.inventory.dto.IngestJobResponse;
import com.jio.eim.inventory.entity.IngestJob;

final class IngestJobMapper {

    private IngestJobMapper() {}

    static IngestJobResponse toResponse(IngestJob job) {
        IngestJobResponse response = new IngestJobResponse();
        response.setJobId(job.getId());
        response.setStatus(job.getStatus());
        response.setFileName(job.getFileName());
        response.setUploadedBy(job.getUploadedBy());
        response.setInputFilePath(job.getInputFilePath());
        response.setOutputFilePath(job.getOutputFilePath());
        response.setRemarks(job.getRemarks());
        response.setTotalRecords(job.getTotalRecords());
        response.setProcessedRecords(job.getProcessedRecords());
        response.setFailedRecords(job.getFailedRecords());
        response.setCreatedAt(job.getCreatedAt());
        response.setCompletedAt(job.getCompletedAt());
        return response;
    }
}

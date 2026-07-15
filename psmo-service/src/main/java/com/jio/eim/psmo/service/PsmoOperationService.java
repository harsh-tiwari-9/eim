package com.jio.eim.psmo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jio.eim.psmo.dto.PsmoCommandMessage;
import com.jio.eim.psmo.dto.PsmoOperationRequest;
import com.jio.eim.psmo.dto.PsmoOperationResponse;
import com.jio.eim.psmo.entity.InventoryDeviceLookup;
import com.jio.eim.psmo.entity.Operation;
import com.jio.eim.psmo.entity.OperationLog;
import com.jio.eim.psmo.repository.InventoryDeviceLookupRepository;
import com.jio.eim.psmo.repository.OperationLogRepository;
import com.jio.eim.psmo.repository.OperationRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PsmoOperationService {
    private static final String STATUS_PENDING = "PENDING";
    private static final String DEVICE_STATUS_DELETED = "DELETED";
    private static final String TYPE_DOWNLOAD = "DOWNLOAD";

    private final OperationRepository operationRepository;
    private final OperationLogRepository operationLogRepository;
    private final InventoryDeviceLookupRepository deviceLookupRepository;
    private final PsmoCommandProducer commandProducer;
    private final ObjectMapper objectMapper;

    public PsmoOperationService(
            OperationRepository operationRepository,
            OperationLogRepository operationLogRepository,
            InventoryDeviceLookupRepository deviceLookupRepository,
            PsmoCommandProducer commandProducer,
            ObjectMapper objectMapper) {
        this.operationRepository = operationRepository;
        this.operationLogRepository = operationLogRepository;
        this.deviceLookupRepository = deviceLookupRepository;
        this.commandProducer = commandProducer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PsmoOperationResponse submit(PsmoOperationRequest request, String requestedBy) {
        InventoryDeviceLookup device = deviceLookupRepository.findById(request.getEid())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Device not found: " + request.getEid()));

        if (DEVICE_STATUS_DELETED.equals(device.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Device not found: " + request.getEid());
        }

        // SGP.32 §6.6: multiple PSMOs may be queued per eUICC.
        // The IPAd retrieves and executes them in order on its next poll.
        // No single-pending check — we simply append to the device's queue.

        // DOWNLOAD is not a PSMO: it carries an activation code and results in an unsigned
        // ProfileDownloadTriggerRequest (BF54). A leading "LPA:" scheme prefix is stripped; a blank
        // code means "contact the default SM-DP+".
        String activationCode = null;
        if (TYPE_DOWNLOAD.equals(request.getType())) {
            activationCode = normalizeActivationCode(request.getActivationCode());
        }

        Operation operation = new Operation();
        operation.setEid(request.getEid());
        operation.setType(request.getType());
        operation.setTargetIccid(request.getTargetIccid());
        if (activationCode != null) {
            operation.setParams(activationCodeParams(activationCode));
        }
        operation.setStatus(STATUS_PENDING);
        operation.setRequestedBy(requestedBy);
        operation = operationRepository.save(operation);

        writeLog(operation.getId(), "CREATED", requestedBy, null);

        PsmoCommandMessage message = new PsmoCommandMessage(
                operation.getId(),
                operation.getEid(),
                operation.getType(),
                operation.getTargetIccid(),
                activationCode,
                requestedBy,
                Instant.now());
        commandProducer.send(message);

        return toResponse(operation);
    }

    @Transactional(readOnly = true)
    public PsmoOperationResponse get(long id) {
        Operation operation = operationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Operation not found: " + id));
        return toResponse(operation);
    }

    @Transactional(readOnly = true)
    public List<PsmoOperationResponse> listForDevice(String eid) {
        return operationRepository.findByEidOrderByCreatedAtDesc(eid).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Trims and drops a leading {@code LPA:} scheme prefix; returns null for a blank code. */
    private static String normalizeActivationCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String code = raw.trim();
        if (code.regionMatches(true, 0, "LPA:", 0, 4)) {
            code = code.substring(4).trim();
        }
        return code.isEmpty() ? null : code;
    }

    private String activationCodeParams(String activationCode) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("activationCode", activationCode);
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize download params", ex);
        }
    }

    private void writeLog(Long operationId, String eventType, String actor, String details) {
        OperationLog log = new OperationLog();
        log.setOperationId(operationId);
        log.setEventType(eventType);
        log.setActor(actor);
        log.setDetails(details);
        operationLogRepository.save(log);
    }

    private PsmoOperationResponse toResponse(Operation o) {
        PsmoOperationResponse r = new PsmoOperationResponse();
        r.setOperationId(o.getId());
        r.setEid(o.getEid());
        r.setType(o.getType());
        r.setTargetIccid(o.getTargetIccid());
        r.setStatus(o.getStatus());
        r.setRequestedBy(o.getRequestedBy());
        r.setResultPayload(o.getResultPayload());
        r.setCreatedAt(o.getCreatedAt());
        r.setUpdatedAt(o.getUpdatedAt());
        r.setSignedAt(o.getSignedAt());
        r.setSentAt(o.getSentAt());
        r.setCompletedAt(o.getCompletedAt());
        return r;
    }
}
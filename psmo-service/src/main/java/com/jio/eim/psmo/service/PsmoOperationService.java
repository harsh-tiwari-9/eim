package com.jio.eim.psmo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PsmoOperationService {
    private static final String STATUS_PENDING = "PENDING";
    private static final String DEVICE_STATUS_DELETED = "DELETED";
    private static final String TYPE_UPDATE_POLLING_INTERVAL = "UPDATE_POLLING_INTERVAL";
    private static final String PARAM_POLLING_INTERVAL = "pollingIntervalSeconds";
    // SGP.32 IPAd polling cadence bounds: 1 minute .. 24 hours
    private static final long MIN_POLLING_INTERVAL_SECONDS = 60;
    private static final long MAX_POLLING_INTERVAL_SECONDS = 86400;

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

        if (TYPE_UPDATE_POLLING_INTERVAL.equals(request.getType())) {
            validatePollingInterval(request.getParams());
        }

        String paramsJson = serializeParams(request.getParams());

        // SGP.32 §6.6: multiple PSMOs may be queued per eUICC.
        // The IPAd retrieves and executes them in order on its next poll.
        // No single-pending check — we simply append to the device's queue.

        Operation operation = new Operation();
        operation.setEid(request.getEid());
        operation.setType(request.getType());
        operation.setTargetIccid(request.getTargetIccid());
        operation.setParams(paramsJson);
        operation.setStatus(STATUS_PENDING);
        operation.setRequestedBy(requestedBy);
        operation = operationRepository.save(operation);

        writeLog(operation.getId(), "CREATED", requestedBy, paramsJson);

        PsmoCommandMessage message = new PsmoCommandMessage(
                operation.getId(),
                operation.getEid(),
                operation.getType(),
                operation.getTargetIccid(),
                requestedBy,
                Instant.now(),
                request.getParams());
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

    private void validatePollingInterval(Map<String, Object> params) {
        Object raw = params == null ? null : params.get(PARAM_POLLING_INTERVAL);
        if (!(raw instanceof Number)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    PARAM_POLLING_INTERVAL + " is required for UPDATE_POLLING_INTERVAL");
        }
        long seconds = ((Number) raw).longValue();
        if (seconds < MIN_POLLING_INTERVAL_SECONDS || seconds > MAX_POLLING_INTERVAL_SECONDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    PARAM_POLLING_INTERVAL + " must be between " + MIN_POLLING_INTERVAL_SECONDS
                            + " and " + MAX_POLLING_INTERVAL_SECONDS + " seconds");
        }
    }

    private String serializeParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid params", ex);
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
        r.setParams(o.getParams());
        r.setResultPayload(o.getResultPayload());
        r.setCreatedAt(o.getCreatedAt());
        r.setUpdatedAt(o.getUpdatedAt());
        r.setSignedAt(o.getSignedAt());
        r.setSentAt(o.getSentAt());
        r.setCompletedAt(o.getCompletedAt());
        return r;
    }
}
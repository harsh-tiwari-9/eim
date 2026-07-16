package com.jio.eim.psmo.service;

import com.jio.eim.psmo.dto.PagedResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PsmoOperationService {
    private static final String STATUS_PENDING = "PENDING";
    private static final String DEVICE_STATUS_DELETED = "DELETED";

    private final OperationRepository operationRepository;
    private final OperationLogRepository operationLogRepository;
    private final InventoryDeviceLookupRepository deviceLookupRepository;
    private final PsmoCommandProducer commandProducer;

    public PsmoOperationService(
            OperationRepository operationRepository,
            OperationLogRepository operationLogRepository,
            InventoryDeviceLookupRepository deviceLookupRepository,
            PsmoCommandProducer commandProducer) {
        this.operationRepository = operationRepository;
        this.operationLogRepository = operationLogRepository;
        this.deviceLookupRepository = deviceLookupRepository;
        this.commandProducer = commandProducer;
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

        Operation operation = new Operation();
        operation.setEid(request.getEid());
        operation.setType(request.getType());
        operation.setTargetIccid(request.getTargetIccid());
        operation.setStatus(STATUS_PENDING);
        operation.setRequestedBy(requestedBy);
        operation = operationRepository.save(operation);

        writeLog(operation.getId(), "CREATED", requestedBy, null);

        PsmoCommandMessage message = new PsmoCommandMessage(
                operation.getId(),
                operation.getEid(),
                operation.getType(),
                operation.getTargetIccid(),
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

    /** Paginated operation history for the UI ops/logs page; all filters optional. */
    @Transactional(readOnly = true)
    public PagedResponse<PsmoOperationResponse> list(String eid, String type, String status, Pageable pageable) {
        Page<Operation> page = operationRepository.search(
                blankToNull(eid), blankToNull(type), blankToNull(status), pageable);
        List<PsmoOperationResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .toList();
        return PagedResponse.from(page, content);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
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
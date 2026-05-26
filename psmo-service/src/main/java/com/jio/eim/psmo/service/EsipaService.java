package com.jio.eim.psmo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jio.eim.psmo.dto.EsipaNotification;
import com.jio.eim.psmo.entity.DevicePending;
import com.jio.eim.psmo.entity.Operation;
import com.jio.eim.psmo.entity.OperationLog;
import com.jio.eim.psmo.entity.SignedPackage;
import com.jio.eim.psmo.repository.DevicePendingRepository;
import com.jio.eim.psmo.repository.OperationLogRepository;
import com.jio.eim.psmo.repository.OperationRepository;
import com.jio.eim.psmo.repository.SignedPackageRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@RequiredArgsConstructor
public class EsipaService {

    private static final String STATUS_SIGNED = "SIGNED";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_EXECUTED = "EXECUTED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String ACTOR = "esipa-service";

    private final DevicePendingRepository devicePendingRepository;
    private final SignedPackageRepository signedPackageRepository;
    private final OperationRepository operationRepository;
    private final OperationLogRepository operationLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Optional<byte[]> getNextPackage(String eid, List<EsipaNotification> lastResults) {
        if (eid == null || eid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eid is required");
        }

        if(lastResults != null) {
            for(EsipaNotification n : lastResults) {
                applyNotification(n);
            }
        }

        Optional<DevicePending> pendingOpt = devicePendingRepository.findFirstByEidOrderByQueuedAtAsc(eid);
        if(pendingOpt.isEmpty()) {
            return Optional.empty();
        }
        DevicePending pending = pendingOpt.get();

        SignedPackage pkg = signedPackageRepository.findById(pending.getOperationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Pending op " + pending.getOperationId() + " has no signed_package row"));

        Operation op = operationRepository.findById(pending.getOperationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Pending op " + pending.getOperationId() + " has no operations row"));

        if (STATUS_SIGNED.equals(op.getStatus())) {
            op.setStatus(STATUS_SENT);
            op.setSentAt(Instant.now());
            operationRepository.save(op);
            writeLog(op.getId(), "DISPATCHED");
            log.info("Dispatched op {} to device {}", op.getId(), eid);
        } else {
            log.debug("Re-serving op {} (status={}) to device {}", op.getId(), op.getStatus(), eid);
        }

        return Optional.of(pkg.getPackageBytes());
    }

    @Transactional
    public void applyNotification(EsipaNotification n) {
        if (n == null || n.getOpId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "opId is required");
        }

        Operation op = operationRepository.findById(n.getOpId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Operation not found: " + n.getOpId()));

        if (STATUS_EXECUTED.equals(op.getStatus()) || STATUS_FAILED.equals(op.getStatus())) {
            log.info("Op {} already terminal ({}), ignoring duplicate notification",
                    op.getId(), op.getStatus());
            return;
        }

        String newStatus = n.getStatus();
        if (!STATUS_EXECUTED.equals(newStatus) && !STATUS_FAILED.equals(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid notification status: " + newStatus);
        }

        op.setStatus(newStatus);
        op.setCompletedAt(Instant.now());
        if (n.getResult() != null) {
            try {
                op.setResultPayload(objectMapper.writeValueAsString(n.getResult()));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Failed to serialize notification result", ex);
            }
        }
        operationRepository.save(op);

        devicePendingRepository.deleteByOperationId(n.getOpId());

        writeLog(op.getId(), "NOTIFICATION_RECEIVED");
        writeLog(op.getId(), newStatus);
        log.info("Op {} {} (notification from device {})", op.getId(), newStatus, op.getEid());
    }

    private void writeLog(Long operationId, String eventType) {
        OperationLog entry = new OperationLog();
        entry.setOperationId(operationId);
        entry.setEventType(eventType);
        entry.setActor(ACTOR);
        operationLogRepository.save(entry);
    }
}
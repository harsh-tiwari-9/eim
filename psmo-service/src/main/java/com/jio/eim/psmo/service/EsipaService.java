package com.jio.eim.psmo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jio.eim.psmo.dto.EsipaNotification;
import com.jio.eim.psmo.esipa.EuiccPackageResultDecoder;
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
    private static final String TYPE_DOWNLOAD = "DOWNLOAD";
    private static final String TYPE_AUDIT = "AUDIT";
    private static final String ACTOR = "esipa-service";

    private final DevicePendingRepository devicePendingRepository;
    private final SignedPackageRepository signedPackageRepository;
    private final OperationRepository operationRepository;
    private final OperationLogRepository operationLogRepository;
    private final EuiccPackageResultDecoder resultDecoder;
    private final ObjectMapper objectMapper;
    private final InventoryProfileSyncService inventoryProfileSyncService;

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

            // A DOWNLOAD trigger produces no eIM-visible result in direct mode (the eUICC reports
            // the install to the SM-DP+, not to us), so dequeue it on first delivery to avoid
            // re-triggering the download on every subsequent poll. Confirm the install via AUDIT.
            if (TYPE_DOWNLOAD.equals(op.getType())) {
                devicePendingRepository.deleteByOperationId(op.getId());
                writeLog(op.getId(), "DOWNLOAD_TRIGGER_DELIVERED");
                log.info("Delivered download trigger for op {} to device {} (dequeued, one-shot)",
                        op.getId(), eid);
            }
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

    /**
     * Applies an eIM Package Result delivered by the device over the spec ESipa interface
     * ({@code ESipa.ProvideEimPackageResult}). The SGP.32 {@code EuiccPackageResult} carries no
     * operation id directly, so it is linked back to the {@link Operation} via the
     * {@code eimTransactionId} the eUICC echoes (which this eIM set to the operationId). For AUDIT
     * the decoded profile list is stored in the operation's result payload.
     */
    /**
     * @return the result {@code seqNumber}(s) to acknowledge back to the eUICC (via
     *         {@code eimAcknowledgements}) so it deletes the stored result and stops re-delivering
     *         it on every poll. Empty when there is nothing safe to acknowledge (undecodable, no
     *         seqNumber, or an unresolved operation).
     */
    @Transactional
    public List<Integer> applyEuiccPackageResult(String eid, byte[] euiccPackageResult) {
        if (euiccPackageResult == null || euiccPackageResult.length == 0) {
            log.warn("Empty eIM Package Result from device {}", eid);
            return List.of();
        }

        EuiccPackageResultDecoder.Decoded decoded = resultDecoder.decode(euiccPackageResult);
        Integer seqNumber = decoded.sequenceNumber();

        if (decoded.operationId() == null) {
            log.warn("eIM Package Result from device {} has no eimTransactionId; cannot map to an "
                    + "operation (seqNumber={}, not acknowledging). details={}",
                    eid, seqNumber, decoded.details());
            return List.of();
        }

        Operation op = operationRepository.findById(decoded.operationId()).orElse(null);
        if (op == null) {
            log.warn("eIM Package Result references unknown operation {} (device {}, seqNumber={}); "
                    + "not acknowledging", decoded.operationId(), eid, seqNumber);
            return List.of();
        }

        // We have the result durably (or already did) — acknowledge its seqNumber so the eUICC
        // stops re-delivering it. Nothing to ack for results that carry no seqNumber (e.g. errors).
        List<Integer> acknowledgements = (seqNumber != null) ? List.of(seqNumber) : List.of();

        if (STATUS_EXECUTED.equals(op.getStatus()) || STATUS_FAILED.equals(op.getStatus())) {
            log.info("Op {} already terminal ({}); acknowledging duplicate result (seqNumber={})",
                    op.getId(), op.getStatus(), seqNumber);
            return acknowledgements;
        }

        String newStatus = decoded.success() ? STATUS_EXECUTED : STATUS_FAILED;
        op.setStatus(newStatus);
        op.setCompletedAt(Instant.now());
        try {
            op.setResultPayload(objectMapper.writeValueAsString(decoded.details()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize eUICC Package Result", ex);
        }
        operationRepository.save(op);
        devicePendingRepository.deleteByOperationId(op.getId());

        writeLog(op.getId(), "RESULT_RECEIVED");
        writeLog(op.getId(), newStatus);
        log.info("Op {} {} from eUICC Package Result (device {}); acknowledging seqNumber {}",
                op.getId(), newStatus, eid, seqNumber);

        // Reconcile inventory.device_profiles with the on-card truth from a successful AUDIT.
        // Runs in its own transaction; best-effort, so a sync failure never fails the ack/status.
        if (decoded.success() && TYPE_AUDIT.equals(op.getType())) {
            try {
                inventoryProfileSyncService.syncFromAuditDetails(eid, decoded.details());
            } catch (Exception ex) {
                log.warn("Failed to sync device_profiles for {} from AUDIT op {} — inventory may be "
                        + "stale, operation result unaffected", eid, op.getId(), ex);
            }
        }
        return acknowledgements;
    }

    private void writeLog(Long operationId, String eventType) {
        OperationLog entry = new OperationLog();
        entry.setOperationId(operationId);
        entry.setEventType(eventType);
        entry.setActor(ACTOR);
        operationLogRepository.save(entry);
    }
}
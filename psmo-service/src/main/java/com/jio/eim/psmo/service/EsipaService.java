package com.jio.eim.psmo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jio.eim.psmo.dto.EsipaNotification;
import com.jio.eim.psmo.entity.DevicePending;
import com.jio.eim.psmo.entity.InventoryDeviceLookup;
import com.jio.eim.psmo.entity.Operation;
import com.jio.eim.psmo.entity.OperationLog;
import com.jio.eim.psmo.entity.SignedPackage;
import com.jio.eim.psmo.esipa.EsipaCodec;
import com.jio.eim.psmo.repository.DevicePendingRepository;
import com.jio.eim.psmo.repository.InventoryDeviceLookupRepository;
import com.jio.eim.psmo.repository.OperationLogRepository;
import com.jio.eim.psmo.repository.OperationRepository;
import com.jio.eim.psmo.repository.SignedPackageRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final String DEVICE_STATUS_DELETED = "DELETED";

    // SGP.32 ESipa polling audit trail — one structured line per poll / result,
    // routed to the dedicated psmo-polling-history.log (see logback-spring.xml).
    private static final Logger POLLING_LOG = LoggerFactory.getLogger("POLLING_HISTORY");

    private final DevicePendingRepository devicePendingRepository;
    private final SignedPackageRepository signedPackageRepository;
    private final OperationRepository operationRepository;
    private final OperationLogRepository operationLogRepository;
    private final InventoryDeviceLookupRepository deviceLookupRepository;
    private final ObjectMapper objectMapper;
    private final EsipaCodec esipaCodec;

    @Transactional
    public Optional<byte[]> getNextPackage(String eid, List<EsipaNotification> lastResults) {
        if (eid == null || eid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eid is required");
        }

        int resultsCount = lastResults == null ? 0 : lastResults.size();
        if(lastResults != null) {
            for(EsipaNotification n : lastResults) {
                applyNotification(n);
            }
        }

        Optional<DevicePending> pendingOpt = devicePendingRepository.findFirstByEidOrderByQueuedAtAsc(eid);
        if(pendingOpt.isEmpty()) {
            logPoll(eid, false, null, null, "NONE", resultsCount);
            return Optional.empty();
        }
        DevicePending pending = pendingOpt.get();

        SignedPackage pkg = signedPackageRepository.findById(pending.getOperationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Pending op " + pending.getOperationId() + " has no signed_package row"));

        Operation op = operationRepository.findById(pending.getOperationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Pending op " + pending.getOperationId() + " has no operations row"));

        String action;
        if (STATUS_SIGNED.equals(op.getStatus())) {
            op.setStatus(STATUS_SENT);
            op.setSentAt(Instant.now());
            operationRepository.save(op);
            writeLog(op.getId(), "DISPATCHED");
            log.info("Dispatched op {} to device {}", op.getId(), eid);
            action = "DISPATCHED";
        } else {
            log.debug("Re-serving op {} (status={}) to device {}", op.getId(), op.getStatus(), eid);
            action = "RE-SERVED";
        }

        logPoll(eid, true, op.getId(), op.getType(), action, resultsCount);
        return Optional.of(pkg.getPackageBytes());
    }

    // ----- SGP.32-compliant ESipa polling loop (ASN.1 binding, §6.3) --------------------

    /**
     * Builds the DER {@code EsipaMessageFromEimToIpa : getEimPackageResponse} for an
     * {@code ESipa.GetEimPackage} poll. Returns {@code eimPackageError: noEimPackageAvailable}
     * when the device has nothing queued (the normal idle-poll outcome), or a
     * {@code euiccPackageRequest} wrapping the stored signed package when an operation is
     * pending. Every poll is recorded in the polling-history log.
     */
    @Transactional
    public byte[] buildGetEimPackageResponse(String eidHex) {
        try {
            if (eidHex == null || eidHex.isBlank()) {
                logPoll("-", false, null, null, "MISSING_EID", 0);
                return esipaCodec.encodeGetEimPackageError(EsipaCodec.ERR_MISSING_EID);
            }

            Optional<InventoryDeviceLookup> device = deviceLookupRepository.findById(eidHex);
            if (device.isEmpty() || DEVICE_STATUS_DELETED.equals(device.get().getStatus())) {
                logPoll(eidHex, false, null, null, "EID_NOT_FOUND", 0);
                return esipaCodec.encodeGetEimPackageError(EsipaCodec.ERR_EID_NOT_FOUND);
            }

            Optional<DevicePending> pendingOpt =
                    devicePendingRepository.findFirstByEidOrderByQueuedAtAsc(eidHex);
            if (pendingOpt.isEmpty()) {
                logPoll(eidHex, false, null, null, "NONE", 0);
                return esipaCodec.encodeGetEimPackageError(EsipaCodec.ERR_NO_PACKAGE_AVAILABLE);
            }
            DevicePending pending = pendingOpt.get();

            SignedPackage pkg = signedPackageRepository.findById(pending.getOperationId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Pending op " + pending.getOperationId() + " has no signed_package row"));
            Operation op = operationRepository.findById(pending.getOperationId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Pending op " + pending.getOperationId() + " has no operations row"));

            String action;
            if (STATUS_SIGNED.equals(op.getStatus())) {
                op.setStatus(STATUS_SENT);
                op.setSentAt(Instant.now());
                operationRepository.save(op);
                writeLog(op.getId(), "DISPATCHED");
                action = "DISPATCHED";
            } else {
                action = "RE-SERVED";
            }
            logPoll(eidHex, true, op.getId(), op.getType(), action, 0);
            return esipaCodec.encodeGetEimPackageResponse(pkg.getPackageBytes());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to encode getEimPackageResponse", ex);
        }
    }

    /**
     * Handles {@code ESipa.ProvideEimPackageResult}. For this round the device-returned
     * {@code euiccPackageResult} is recorded and acknowledged; correlating it back to a
     * specific operation requires parsing the (eventually signed) euiccPackageResult and is
     * deferred with the real-PKI work.
     */
    @Transactional
    public byte[] handleProvideEimPackageResult(String eidHex) {
        try {
            POLLING_LOG.info("ts={} eid={} event=RESULT op={} status={}",
                    Instant.now(), eidHex == null ? "-" : eidHex, "-", "RECEIVED");
            return esipaCodec.encodeProvideResultAck();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to encode provideEimPackageResultResponse", ex);
        }
    }

    /** Handles {@code ESipa.HandleNotification}; the HTTP response body SHALL be empty (§6.1.2). */
    public void handleNotification(String eidHex) {
        POLLING_LOG.info("ts={} eid={} event=NOTIFICATION",
                Instant.now(), eidHex == null ? "-" : eidHex);
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
        POLLING_LOG.info("ts={} eid={} event=RESULT op={} status={}",
                Instant.now(), op.getEid(), op.getId(), newStatus);
        log.info("Op {} {} (notification from device {})", op.getId(), newStatus, op.getEid());
    }

    /**
     * SGP.32 ESipa poll audit line. {@code served} indicates whether an eIM Package was
     * returned; {@code op}/{@code type} identify the PSMO served (null for an empty poll);
     * {@code results} is the count of device-returned notifications (eimPackageResult /
     * NotificationList) submitted on this poll.
     */
    private void logPoll(String eid, boolean served, Long operationId, String type,
                         String action, int resultsCount) {
        POLLING_LOG.info("ts={} eid={} served={} op={} type={} action={} results={}",
                Instant.now(), eid, served,
                operationId == null ? "-" : operationId,
                type == null ? "-" : type,
                action, resultsCount);
    }

    private void writeLog(Long operationId, String eventType) {
        OperationLog entry = new OperationLog();
        entry.setOperationId(operationId);
        entry.setEventType(eventType);
        entry.setActor(ACTOR);
        operationLogRepository.save(entry);
    }
}
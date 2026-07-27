package com.jio.eim.psmo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jio.eim.psmo.dto.PagedResponse;
import com.jio.eim.psmo.dto.ProfileInfoResponse;
import com.jio.eim.psmo.dto.PsmoCommandMessage;
import com.jio.eim.psmo.dto.PsmoOperationRequest;
import com.jio.eim.psmo.dto.PsmoOperationResponse;
import com.jio.eim.psmo.entity.InventoryDeviceLookup;
import com.jio.eim.psmo.entity.InventoryDeviceProfile;
import com.jio.eim.psmo.entity.Operation;
import com.jio.eim.psmo.entity.OperationLog;
import com.jio.eim.psmo.repository.InventoryDeviceLookupRepository;
import com.jio.eim.psmo.repository.InventoryDeviceProfileRepository;
import com.jio.eim.psmo.repository.OperationLogRepository;
import com.jio.eim.psmo.repository.OperationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PsmoOperationService {
    private static final String STATUS_PENDING = "PENDING";
    private static final String DEVICE_STATUS_DELETED = "DELETED";
    private static final String TYPE_DOWNLOAD = "DOWNLOAD";
    private static final String TYPE_DISABLE = "DISABLE";

    private final OperationRepository operationRepository;
    private final OperationLogRepository operationLogRepository;
    private final InventoryDeviceLookupRepository deviceLookupRepository;
    private final PsmoCommandProducer commandProducer;
    private final ObjectMapper objectMapper;
    private final OperationIdGenerator operationIdGenerator;
    private final InventoryDeviceProfileRepository deviceProfileRepository;

    public PsmoOperationService(
            OperationRepository operationRepository,
            OperationLogRepository operationLogRepository,
            InventoryDeviceLookupRepository deviceLookupRepository,
            PsmoCommandProducer commandProducer,
            ObjectMapper objectMapper,
            OperationIdGenerator operationIdGenerator,
            InventoryDeviceProfileRepository deviceProfileRepository) {
        this.operationRepository = operationRepository;
        this.operationLogRepository = operationLogRepository;
        this.deviceLookupRepository = deviceLookupRepository;
        this.commandProducer = commandProducer;
        this.objectMapper = objectMapper;
        this.operationIdGenerator = operationIdGenerator;
        this.deviceProfileRepository = deviceProfileRepository;
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

        // DISABLE is executed as "enable the other profile" (single-port eUICC): enabling enableIccid
        // implicitly disables targetIccid, without stranding the device. Recorded as DISABLE.
        String enableIccid = null;
        if (TYPE_DISABLE.equals(request.getType())) {
            enableIccid = blankToNull(request.getEnableIccid());
            if (enableIccid == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "DISABLE requires enableIccid (the profile to enable in place of the disabled one)");
            }
        }

        Operation operation = new Operation();
        operation.setId(operationIdGenerator.next());
        operation.setEid(request.getEid());
        operation.setType(request.getType());
        operation.setTargetIccid(request.getTargetIccid());
        if (activationCode != null) {
            operation.setParams(activationCodeParams(activationCode));
        } else if (enableIccid != null) {
            operation.setParams(enableIccidParams(enableIccid));
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
                enableIccid,
                activationCode,
                requestedBy,
                Instant.now());
        commandProducer.send(message);

        return toResponse(operation);
    }

    /**
     * Queues a system-initiated AUDIT so the profile view re-syncs with the card after a successful
     * state-changing operation (enable/disable/delete/download). Executes on the device's next poll.
     * Runs in its own transaction so a caller in a result-handling transaction is never affected.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void queueAudit(String eid, String reason) {
        Operation audit = new Operation();
        audit.setId(operationIdGenerator.next());
        audit.setEid(eid);
        audit.setType("AUDIT");
        audit.setStatus(STATUS_PENDING);
        audit.setRequestedBy("system:auto-sync");
        audit = operationRepository.save(audit);
        writeLog(audit.getId(), "CREATED", "system:auto-sync", reason);
        commandProducer.send(new PsmoCommandMessage(
                audit.getId(), eid, "AUDIT", null, null, null, "system:auto-sync", Instant.now()));
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

    /**
     * Returns the current state of the given operations, for the UI to refresh the rows it is
     * showing. Unknown ids are silently skipped; results are ordered to match the requested ids.
     */
    @Transactional(readOnly = true)
    public List<PsmoOperationResponse> refresh(List<Long> operationIds) {
        Map<Long, Operation> byId = operationRepository.findByIdIn(operationIds).stream()
                .collect(Collectors.toMap(Operation::getId, o -> o));
        return operationIds.stream()
                .distinct()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(this::toResponse)
                .toList();
    }

    /**
     * On-card profile information for a device, taken from its most recent successful AUDIT. Returns
     * an empty profile list (with {@code auditedAt = null}) if the device has never been audited.
     */
    @Transactional(readOnly = true)
    public ProfileInfoResponse profiles(String eid) {
        ProfileInfoResponse response = new ProfileInfoResponse();
        response.setEid(eid);

        List<InventoryDeviceProfile> rows = deviceProfileRepository.findByEid(eid);
        List<ProfileInfoResponse.Profile> profiles = new ArrayList<>();
        Instant latest = null;
        for (InventoryDeviceProfile r : rows) {
            ProfileInfoResponse.Profile p = new ProfileInfoResponse.Profile();
            p.setIccid(r.getIccid());
            p.setState(r.getState());
            p.setFallbackAttribute(r.isFallback());
            p.setFallbackAllowed(r.isFallbackAllowed());
            p.setProfileClass(r.getProfileClassName());
            p.setLabel(r.getLabel());
            p.setProfileName(r.getProfileName());
            p.setServiceProviderName(r.getServiceProviderName());
            profiles.add(p);
            if (r.getUpdatedAt() != null && (latest == null || r.getUpdatedAt().isAfter(latest))) {
                latest = r.getUpdatedAt();
            }
        }
        response.setProfiles(profiles);
        response.setAuditedAt(latest);  // "as of" — when device_profiles was last synced/updated
        return response;
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

    /** Records the profile enabled under the hood for a DISABLE, for traceability in the op record. */
    private String enableIccidParams(String enableIccid) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("enableIccid", enableIccid);
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize disable params", ex);
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
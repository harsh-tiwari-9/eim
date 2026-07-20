package com.jio.eim.psmo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jio.eim.psmo.dto.PagedResponse;
import com.jio.eim.psmo.dto.ProfileInfoResponse;
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
import java.util.stream.Collectors;
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
        response.setProfiles(List.of());

        Operation audit = operationRepository
                .findFirstByEidAndTypeAndStatusOrderByCompletedAtDesc(eid, "AUDIT", "EXECUTED")
                .orElse(null);
        if (audit == null || audit.getResultPayload() == null) {
            return response;  // never audited (or no payload) — empty snapshot
        }

        response.setAuditedAt(audit.getCompletedAt());
        response.setAuditOperationId(audit.getId());
        response.setProfiles(parseProfiles(audit.getResultPayload()));
        return response;
    }

    /** Extracts the listProfileInfo profile list out of an AUDIT result_payload JSON string. */
    private List<ProfileInfoResponse.Profile> parseProfiles(String resultPayloadJson) {
        try {
            JsonNode results = objectMapper.readTree(resultPayloadJson).path("results");
            for (JsonNode result : results) {
                if ("listProfileInfo".equals(result.path("type").asText())) {
                    List<ProfileInfoResponse.Profile> out = new java.util.ArrayList<>();
                    for (JsonNode p : result.path("profiles")) {
                        ProfileInfoResponse.Profile profile = new ProfileInfoResponse.Profile();
                        profile.setIccid(text(p, "iccid"));
                        profile.setState(text(p, "state"));
                        profile.setProfileClass(text(p, "profileClass"));
                        profile.setLabel(text(p, "label"));
                        profile.setProfileName(text(p, "profileName"));
                        profile.setServiceProviderName(text(p, "serviceProviderName"));
                        profile.setFallbackAttribute(p.path("fallbackAttribute").asBoolean(false));
                        profile.setFallbackAllowed(p.path("fallbackAllowed").asBoolean(false));
                        out.add(profile);
                    }
                    return out;
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse AUDIT result payload", ex);
        }
        return List.of();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
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
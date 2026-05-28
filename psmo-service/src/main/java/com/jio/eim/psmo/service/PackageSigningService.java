package com.jio.eim.psmo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jio.eim.psmo.dto.PsmoCommandMessage;
import com.jio.eim.psmo.entity.DevicePending;
import com.jio.eim.psmo.entity.Operation;
import com.jio.eim.psmo.entity.OperationLog;
import com.jio.eim.psmo.entity.SignedPackage;
import com.jio.eim.psmo.repository.DevicePendingRepository;
import com.jio.eim.psmo.repository.OperationLogRepository;
import com.jio.eim.psmo.repository.OperationRepository;
import com.jio.eim.psmo.repository.SignedPackageRepository;
import com.jio.eim.psmo.signer.BuiltPackage;
import com.jio.eim.psmo.signer.PackageBuilder;
import com.jio.eim.psmo.signer.Signer;
import com.jio.eim.psmo.signer.Signer.SignatureResult;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PackageSigningService {

    private static final Logger log = LoggerFactory.getLogger(PackageSigningService.class);

    private static final String STATUS_SIGNED = "SIGNED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String PACKAGE_FORMAT_ASN1 = "ASN1";
    private static final String ACTOR = "package-signer";

    private final OperationRepository operationRepository;
    private final OperationLogRepository operationLogRepository;
    private final SignedPackageRepository signedPackageRepository;
    private final DevicePendingRepository devicePendingRepository;
    private final PackageBuilder packageBuilder;
    private final Signer signer;
    private final ObjectMapper objectMapper;

    public PackageSigningService(
            OperationRepository operationRepository,
            OperationLogRepository operationLogRepository,
            SignedPackageRepository signedPackageRepository,
            DevicePendingRepository devicePendingRepository,
            PackageBuilder packageBuilder,
            Signer signer,
            ObjectMapper objectMapper) {
        this.operationRepository = operationRepository;
        this.operationLogRepository = operationLogRepository;
        this.signedPackageRepository = signedPackageRepository;
        this.devicePendingRepository = devicePendingRepository;
        this.packageBuilder = packageBuilder;
        this.signer = signer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void process(PsmoCommandMessage message) {
        Operation operation = operationRepository.findById(message.operationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Operation not found for Kafka message: " + message.operationId()));

        if (signedPackageRepository.findById(operation.getId()).isPresent()) {
            log.info("Operation {} already signed, skipping (idempotent retry)", operation.getId());
            return;
        }

        try {
            BuiltPackage built = packageBuilder.build(message);
            SignatureResult sig = signer.sign(built.toBeSigned());
            byte[] finalBytes = packageBuilder.attachSignature(built, sig.signature());

            SignedPackage sp = new SignedPackage();
            sp.setOperationId(operation.getId());
            sp.setPackageBytes(finalBytes);
            sp.setPackageFormat(PACKAGE_FORMAT_ASN1);
            sp.setSignatureAlg(sig.algorithm());
            sp.setSignature(sig.signature().length == 0 ? null : sig.signature());
            signedPackageRepository.save(sp);

            DevicePending pending = new DevicePending();
            pending.setEid(operation.getEid());
            pending.setOperationId(operation.getId());
            devicePendingRepository.save(pending);

            Instant now = Instant.now();
            operation.setStatus(STATUS_SIGNED);
            operation.setSignedAt(now);
            operationRepository.save(operation);

            Map<String, Object> signedDetails = new LinkedHashMap<>();
            signedDetails.put("alg", sig.algorithm());
            signedDetails.put("bytes", finalBytes.length);
            writeLog(operation.getId(), "SIGNED", signedDetails);

            log.info("Operation {} signed and queued for EID {}", operation.getId(), operation.getEid());
        } catch (Exception ex) {
            log.error("Failed to sign operation {}", operation.getId(), ex);
            operation.setStatus(STATUS_FAILED);
            operationRepository.save(operation);
            Map<String, Object> failedDetails = new LinkedHashMap<>();
            failedDetails.put("reason", "sign-failed");
            failedDetails.put("message", ex.getMessage());
            writeLog(operation.getId(), "FAILED", failedDetails);
            // do NOT rethrow — we don't want Kafka to retry indefinitely on a bad package
        }
    }

    private void writeLog(Long operationId, String eventType, Map<String, Object> details) {
        OperationLog entry = new OperationLog();
        entry.setOperationId(operationId);
        entry.setEventType(eventType);
        entry.setActor(ACTOR);
        if (details != null && !details.isEmpty()) {
            try {
                entry.setDetails(objectMapper.writeValueAsString(details));
            } catch (JsonProcessingException ex) {
                log.warn("Failed to serialize log details for op {}", operationId, ex);
            }
        }
        operationLogRepository.save(entry);
    }
}
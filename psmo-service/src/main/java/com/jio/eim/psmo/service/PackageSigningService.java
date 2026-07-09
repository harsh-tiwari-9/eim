package com.jio.eim.psmo.service;

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
    private static final String PACKAGE_FORMAT_DL_TRIGGER = "DL_TRIGGER";
    private static final String SIGNATURE_ALG_NONE = "none";
    private static final String TYPE_DOWNLOAD = "DOWNLOAD";
    private static final String ACTOR = "package-signer";

    private final OperationRepository operationRepository;
    private final OperationLogRepository operationLogRepository;
    private final SignedPackageRepository signedPackageRepository;
    private final DevicePendingRepository devicePendingRepository;
    private final PackageBuilder packageBuilder;
    private final Signer signer;

    public PackageSigningService(
            OperationRepository operationRepository,
            OperationLogRepository operationLogRepository,
            SignedPackageRepository signedPackageRepository,
            DevicePendingRepository devicePendingRepository,
            PackageBuilder packageBuilder,
            Signer signer) {
        this.operationRepository = operationRepository;
        this.operationLogRepository = operationLogRepository;
        this.signedPackageRepository = signedPackageRepository;
        this.devicePendingRepository = devicePendingRepository;
        this.packageBuilder = packageBuilder;
        this.signer = signer;
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
            byte[] finalBytes;
            String packageFormat;
            String signatureAlg;
            byte[] signatureBytes;

            if (TYPE_DOWNLOAD.equals(message.type())) {
                // DOWNLOAD is not a signed EuiccPackage — it's an unsigned ProfileDownloadTriggerRequest.
                finalBytes = packageBuilder.buildProfileDownloadTrigger(message);
                packageFormat = PACKAGE_FORMAT_DL_TRIGGER;
                signatureAlg = SIGNATURE_ALG_NONE;
                signatureBytes = null;
            } else {
                BuiltPackage built = packageBuilder.build(message);
                SignatureResult sig = signer.sign(built.toBeSigned());
                finalBytes = packageBuilder.attachSignature(built, sig.signature());
                packageFormat = PACKAGE_FORMAT_ASN1;
                signatureAlg = sig.algorithm();
                signatureBytes = sig.signature().length == 0 ? null : sig.signature();
            }

            SignedPackage sp = new SignedPackage();
            sp.setOperationId(operation.getId());
            sp.setPackageBytes(finalBytes);
            sp.setPackageFormat(packageFormat);
            sp.setSignatureAlg(signatureAlg);
            sp.setSignature(signatureBytes);
            signedPackageRepository.save(sp);

            DevicePending pending = new DevicePending();
            pending.setEid(operation.getEid());
            pending.setOperationId(operation.getId());
            devicePendingRepository.save(pending);

            // Reuse the SIGNED status so the existing ESipa serve path (which keys on SIGNED)
            // dispatches the download trigger exactly like a signed package.
            Instant now = Instant.now();
            operation.setStatus(STATUS_SIGNED);
            operation.setSignedAt(now);
            operationRepository.save(operation);

            writeLog(operation.getId(), "SIGNED", "fmt=" + packageFormat
                    + " alg=" + signatureAlg + " bytes=" + finalBytes.length);

            log.info("Operation {} ({}) queued for EID {} [{}]",
                    operation.getId(), message.type(), operation.getEid(), packageFormat);
        } catch (Exception ex) {
            log.error("Failed to sign operation {}", operation.getId(), ex);
            operation.setStatus(STATUS_FAILED);
            operationRepository.save(operation);
            writeLog(operation.getId(), "FAILED", "sign-failed: " + ex.getMessage());
            // do NOT rethrow — we don't want Kafka to retry indefinitely on a bad package
        }
    }

    private void writeLog(Long operationId, String eventType, String details) {
        OperationLog log = new OperationLog();
        log.setOperationId(operationId);
        log.setEventType(eventType);
        log.setActor(ACTOR);
        log.setDetails(details == null ? null : "\"" + details.replace("\"", "\\\"") + "\"");
        operationLogRepository.save(log);
    }
}
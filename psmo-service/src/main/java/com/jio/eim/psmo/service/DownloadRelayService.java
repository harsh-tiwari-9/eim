package com.jio.eim.psmo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jio.eim.psmo.entity.DownloadSession;
import com.jio.eim.psmo.entity.Operation;
import com.jio.eim.psmo.esipa.Es9PlusClient;
import com.jio.eim.psmo.esipa.EsipaAsn1Codec;
import com.jio.eim.psmo.repository.DownloadSessionRepository;
import com.jio.eim.psmo.repository.OperationRepository;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.stereotype.Service;

/**
 * Relays the eUICC's indirect profile-download RSP handshake (ESipa) to the SM-DP+ (ES9+), per
 * SGP.32 §6.3.2. The eIM does no crypto — it shuttles opaque DER blobs between the ESipa ASN.1
 * envelopes and the ES9+ JSON (base64) binding, and tracks the session by SM-DP+ transactionId.
 *
 * <p>Stage 1 implements InitiateAuthentication only.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DownloadRelayService {

    private static final String STATUS_INITIATED = "INITIATED";
    private static final String OP_STATUS_FAILED = "FAILED";
    private static final int ERR_UNDEFINED = 127;

    private final EsipaAsn1Codec codec;
    private final Es9PlusClient es9Client;
    private final DownloadSessionRepository sessionRepository;
    private final OperationRepository operationRepository;
    private final ObjectMapper objectMapper;

    /**
     * ESipa.InitiateAuthentication: forward the eUICC challenge/info to the SM-DP+ and return the
     * server's signed authentication material to the eUICC, opening a download session.
     */
    public byte[] handleInitiateAuth(byte[] body) {
        EsipaAsn1Codec.InitiateAuthRequest req = codec.decodeInitiateAuthRequest(body);
        Long operationId = req.eimTransactionId();
        String eid = resolveEid(operationId);
        log.info("Download relay: InitiateAuthentication eid={} op={} smdp={}",
                eid, operationId, req.smdpAddress());

        ObjectNode json = objectMapper.createObjectNode();
        json.put("smdpAddress", req.smdpAddress());
        json.put("euiccChallenge", b64(req.euiccChallenge()));
        if (req.euiccInfo1Der() != null) {
            json.put("euiccInfo1", b64(req.euiccInfo1Der()));
        }

        JsonNode resp = es9Client.call(req.smdpAddress(), "initiateAuthentication", json);
        if (!isSuccess(resp)) {
            log.warn("SM-DP+ initiateAuthentication failed op={} : {}", operationId, statusText(resp));
            failOperation(operationId, "initiateAuthentication failed: " + statusText(resp));
            return codec.encodeInitiateAuthError(ERR_UNDEFINED);
        }

        String transactionIdHex = text(resp, "transactionId");
        byte[] transactionId = Hex.decode(transactionIdHex);
        byte[] serverSigned1 = dec(resp, "serverSigned1");
        byte[] serverSignature1 = dec(resp, "serverSignature1");
        byte[] euiccCiPKId = dec(resp, "euiccCiPKIdToBeUsed");
        byte[] serverCertificate = dec(resp, "serverCertificate");

        DownloadSession session = new DownloadSession();
        session.setTransactionId(transactionIdHex.toLowerCase());
        session.setEid(eid);
        session.setSmdpAddress(req.smdpAddress());
        session.setOperationId(operationId);
        session.setStatus(STATUS_INITIATED);
        sessionRepository.save(session);
        log.info("Download session {} opened (eid={} op={} smdp={})",
                session.getTransactionId(), eid, operationId, req.smdpAddress());

        return codec.encodeInitiateAuthOk(transactionId, serverSigned1, serverSignature1,
                euiccCiPKId, serverCertificate);
    }

    private String resolveEid(Long operationId) {
        if (operationId == null) {
            return "UNKNOWN";
        }
        return operationRepository.findById(operationId).map(Operation::getEid).orElse("UNKNOWN");
    }

    private void failOperation(Long operationId, String reason) {
        if (operationId == null) {
            return;
        }
        operationRepository.findById(operationId).ifPresent(op -> {
            op.setStatus(OP_STATUS_FAILED);
            op.setCompletedAt(Instant.now());
            op.setResultPayload("\"" + reason.replace("\"", "'") + "\"");
            operationRepository.save(op);
        });
    }

    private static boolean isSuccess(JsonNode resp) {
        String status = resp.path("header").path("functionExecutionStatus").path("status").asText("");
        return status.startsWith("Executed");
    }

    private static String statusText(JsonNode resp) {
        return resp.path("header").path("functionExecutionStatus").toString();
    }

    private static String text(JsonNode resp, String field) {
        JsonNode n = resp.get(field);
        if (n == null || n.isNull()) {
            throw new IllegalStateException("SM-DP+ response missing field: " + field);
        }
        return n.asText();
    }

    private static byte[] dec(JsonNode resp, String field) {
        return Base64.getDecoder().decode(text(resp, field));
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
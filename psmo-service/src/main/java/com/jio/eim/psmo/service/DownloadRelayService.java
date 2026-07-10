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
    private static final String STATUS_AUTHENTICATED = "AUTHENTICATED";
    private static final String STATUS_BOUND = "BOUND";
    private static final String STATUS_SESSION_FAILED = "FAILED";
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

    /**
     * ESipa.AuthenticateClient: forward the eUICC's AuthenticateServer response to the SM-DP+ and
     * return the SM-DP+'s signed profile metadata / download material to the eUICC.
     */
    public byte[] handleAuthenticateClient(byte[] body) {
        EsipaAsn1Codec.AuthenticateClientRequest req = codec.decodeAuthenticateClientRequest(body);
        DownloadSession session = lookup(req.transactionIdHex());
        log.info("Download relay: AuthenticateClient session={} op={}",
                session.getTransactionId(), session.getOperationId());

        ObjectNode json = objectMapper.createObjectNode();
        json.put("transactionId", req.transactionIdHex());
        json.put("authenticateServerResponse", b64(req.authenticateServerResponseDer()));

        JsonNode resp = es9Client.call(session.getSmdpAddress(), "authenticateClient", json);
        if (!isSuccess(resp)) {
            log.warn("SM-DP+ authenticateClient failed session={} : {}",
                    session.getTransactionId(), statusText(resp));
            markSessionFailed(session);
            failOperation(session.getOperationId(), "authenticateClient failed: " + statusText(resp));
            return codec.encodeAuthenticateClientError(ERR_UNDEFINED);
        }

        byte[] transactionId = Hex.decode(textOr(resp, "transactionId", req.transactionIdHex()));
        byte[] profileMetadata = optDec(resp, "profileMetadata");
        byte[] smdpSigned2 = dec(resp, "smdpSigned2");
        byte[] smdpSignature2 = dec(resp, "smdpSignature2");
        byte[] smdpCertificate = dec(resp, "smdpCertificate");

        session.setStatus(STATUS_AUTHENTICATED);
        sessionRepository.save(session);

        return codec.encodeAuthenticateClientOkDP(transactionId, profileMetadata,
                smdpSigned2, smdpSignature2, smdpCertificate);
    }

    /**
     * ESipa.GetBoundProfilePackage: forward the eUICC's PrepareDownload response to the SM-DP+ and
     * return the Bound Profile Package for the eUICC to install.
     */
    public byte[] handleGetBoundProfilePackage(byte[] body) {
        EsipaAsn1Codec.GetBoundProfilePackageRequest req = codec.decodeGetBoundProfilePackageRequest(body);
        DownloadSession session = lookup(req.transactionIdHex());
        log.info("Download relay: GetBoundProfilePackage session={} op={}",
                session.getTransactionId(), session.getOperationId());

        ObjectNode json = objectMapper.createObjectNode();
        json.put("transactionId", req.transactionIdHex());
        json.put("prepareDownloadResponse", b64(req.prepareDownloadResponseDer()));

        JsonNode resp = es9Client.call(session.getSmdpAddress(), "getBoundProfilePackage", json);
        if (!isSuccess(resp)) {
            log.warn("SM-DP+ getBoundProfilePackage failed session={} : {}",
                    session.getTransactionId(), statusText(resp));
            markSessionFailed(session);
            failOperation(session.getOperationId(), "getBoundProfilePackage failed: " + statusText(resp));
            return codec.encodeGetBoundProfilePackageError(ERR_UNDEFINED);
        }

        byte[] transactionId = Hex.decode(textOr(resp, "transactionId", req.transactionIdHex()));
        byte[] boundProfilePackage = dec(resp, "boundProfilePackage");

        session.setStatus(STATUS_BOUND);
        sessionRepository.save(session);
        log.info("Bound Profile Package delivered to eUICC session={} op={} ({} bytes) — install "
                + "proceeds on-card; confirm via AUDIT", session.getTransactionId(),
                session.getOperationId(), boundProfilePackage.length);

        return codec.encodeGetBoundProfilePackageOk(transactionId, boundProfilePackage);
    }

    private DownloadSession lookup(String transactionIdHex) {
        String key = transactionIdHex == null ? null : transactionIdHex.toLowerCase();
        return sessionRepository.findById(key)
                .orElseThrow(() -> new IllegalStateException("No download session for transactionId " + key));
    }

    private void markSessionFailed(DownloadSession session) {
        session.setStatus(STATUS_SESSION_FAILED);
        sessionRepository.save(session);
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

    /** Base64-decode an optional field; null if absent/empty. */
    private static byte[] optDec(JsonNode resp, String field) {
        JsonNode n = resp.get(field);
        if (n == null || n.isNull() || n.asText().isEmpty()) {
            return null;
        }
        return Base64.getDecoder().decode(n.asText());
    }

    private static String textOr(JsonNode resp, String field, String fallback) {
        JsonNode n = resp.get(field);
        return (n == null || n.isNull()) ? fallback : n.asText();
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
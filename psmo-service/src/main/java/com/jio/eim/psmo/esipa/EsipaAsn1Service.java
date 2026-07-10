package com.jio.eim.psmo.esipa;

import com.jio.eim.psmo.service.DownloadRelayService;
import com.jio.eim.psmo.service.EsipaService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Bridges the SGP.32 ESipa ASN.1 transport ({@link EsipaAsn1Codec}) to the existing per-EID
 * package queue ({@link EsipaService}). This is the spec-compliant path a real IPAd uses when it
 * polls the eIM (interface ES_eim, {@code POST gsma/rsp2/asn1}).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EsipaAsn1Service {

    private final EsipaAsn1Codec codec;
    private final EsipaService esipaService;
    private final DownloadRelayService downloadRelayService;

    /**
     * Handles one {@code EsipaMessageFromIpaToEim} and returns the DER-encoded
     * {@code EsipaMessageFromEimToIpa} response body. Indirect profile-download relay functions
     * (SGP.32 §6.3.2) are routed to {@link DownloadRelayService}; everything else uses the existing
     * eIM-package retrieval/result path.
     */
    public byte[] handle(byte[] requestBody) {
        int tag = codec.topTag(requestBody);
        if (tag == EsipaAsn1Codec.TAG_INITIATE_AUTHENTICATION) {
            log.info("ESipa relay: InitiateAuthentication");
            return downloadRelayService.handleInitiateAuth(requestBody);
        }

        EsipaAsn1Codec.IpaMessage msg = codec.decodeFromIpa(requestBody);
        return switch (msg.kind()) {
            case GET_EIM_PACKAGE -> {
                Optional<byte[]> pkg = esipaService.getNextPackage(msg.eidHex(), null);
                if (pkg.isPresent()) {
                    log.info("ESipa poll: serving eIM Package to device {}", msg.eidHex());
                } else {
                    log.debug("ESipa poll: no eIM Package available for device {}", msg.eidHex());
                }
                yield codec.encodeGetEimPackageResponse(pkg.orElse(null));
            }
            case PROVIDE_EIM_PACKAGE_RESULT -> {
                List<Integer> acks = esipaService.applyEuiccPackageResult(msg.eidHex(), msg.eimPackageResult());
                if (!acks.isEmpty()) {
                    log.info("ESipa: acknowledging eUICC result seqNumber(s) {} to device {}",
                            acks, msg.eidHex());
                }
                yield codec.encodeProvideEimPackageResultResponse(acks);
            }
        };
    }
}
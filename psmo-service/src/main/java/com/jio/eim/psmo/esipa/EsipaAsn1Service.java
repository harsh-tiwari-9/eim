package com.jio.eim.psmo.esipa;

import com.jio.eim.psmo.service.EsipaService;
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

    /**
     * Handles one {@code EsipaMessageFromIpaToEim} and returns the DER-encoded
     * {@code EsipaMessageFromEimToIpa} response body.
     */
    public byte[] handle(byte[] requestBody) {
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
                esipaService.applyEuiccPackageResult(msg.eidHex(), msg.eimPackageResult());
                yield codec.encodeProvideEimPackageResultResponse();
            }
        };
    }
}
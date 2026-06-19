package com.jio.eim.psmo.esipa;

import com.jio.eim.psmo.esipa.EsipaCodec.DecodedRequest;
import com.jio.eim.psmo.service.EsipaService;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Decodes an inbound {@code EsipaMessageFromIpaToEim} (ASN.1 binding, SGP.32 v1.2 §6.3),
 * routes the selected polling-loop function to {@link EsipaService}, and returns the DER
 * {@code EsipaMessageFromEimToIpa} response. An empty {@link Optional} means the function's
 * HTTP response body SHALL be empty (e.g. HandleNotification, §6.1.2).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EsipaMessageDispatcher {

    private final EsipaCodec codec;
    private final EsipaService esipaService;

    public Optional<byte[]> dispatch(byte[] requestBody) {
        DecodedRequest req;
        try {
            req = codec.decode(requestBody);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed ESipa message", ex);
        }

        return switch (req.function()) {
            case GET_EIM_PACKAGE ->
                    Optional.of(esipaService.buildGetEimPackageResponse(req.eidHex()));
            case PROVIDE_EIM_PACKAGE_RESULT ->
                    Optional.of(esipaService.handleProvideEimPackageResult(req.eidHex()));
            case HANDLE_NOTIFICATION -> {
                esipaService.handleNotification(req.eidHex());
                yield Optional.empty();
            }
            case UNKNOWN -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unsupported ESipa function");
        };
    }
}
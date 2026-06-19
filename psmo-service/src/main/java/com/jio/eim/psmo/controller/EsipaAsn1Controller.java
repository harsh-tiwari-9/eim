package com.jio.eim.psmo.controller;

import com.jio.eim.psmo.esipa.EsipaMessageDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SGP.32 ESipa interface binding over HTTP with the ASN.1 function binding (SGP.32 v1.2 §6.1.1).
 * All ESipa functions using the ASN.1 binding are sent to the generic path {@code gsma/rsp2/asn1}
 * with {@code Content-Type: application/x-gsma-rsp-asn1}; the body is a DER
 * {@code EsipaMessageFromIpaToEim} / {@code EsipaMessageFromEimToIpa}.
 */
@RestController
@RequestMapping("/gsma/rsp2")
@RequiredArgsConstructor
public class EsipaAsn1Controller {

    private static final String CONTENT_TYPE_ASN1 = "application/x-gsma-rsp-asn1";
    private static final String ADMIN_PROTOCOL = "gsma/rsp/v2.5.0";

    private final EsipaMessageDispatcher dispatcher;

    @PostMapping(value = "/asn1", consumes = CONTENT_TYPE_ASN1, produces = CONTENT_TYPE_ASN1)
    public ResponseEntity<byte[]> handle(@RequestBody byte[] body) {
        return dispatcher.dispatch(body)
                .map(resp -> ResponseEntity.ok()
                        .header("X-Admin-Protocol", ADMIN_PROTOCOL)
                        .header(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_ASN1)
                        .body(resp))
                .orElseGet(() -> ResponseEntity.ok()
                        .header("X-Admin-Protocol", ADMIN_PROTOCOL)
                        .build());
    }
}
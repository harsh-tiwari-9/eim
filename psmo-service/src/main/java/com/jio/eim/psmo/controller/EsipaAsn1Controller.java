package com.jio.eim.psmo.controller;

import com.jio.eim.psmo.esipa.EsipaAsn1Service;
import com.jio.eim.psmo.esipa.UnsupportedEsipaFunctionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * SGP.32 ESipa interface binding over HTTP (section 6.1.1). A real IPAd POSTs a DER-encoded
 * {@code EsipaMessageFromIpaToEim} to the generic path {@code gsma/rsp2/asn1} and receives a
 * DER-encoded {@code EsipaMessageFromEimToIpa}. This is the endpoint a device must reach for it
 * to poll this eIM (eim2) — distinct from the simplified JSON lab stub in {@link EsipaController}.
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class EsipaAsn1Controller {

    private static final String ASN1_MEDIA_TYPE = "application/x-gsma-rsp-asn1";
    private static final String ADMIN_PROTOCOL_HEADER = "X-Admin-Protocol";
    private static final String ADMIN_PROTOCOL_VALUE = "gsma/rsp/v2.1.0";

    private final EsipaAsn1Service esipaAsn1Service;

    @PostMapping(value = "/gsma/rsp2/asn1", consumes = ASN1_MEDIA_TYPE, produces = ASN1_MEDIA_TYPE)
    public ResponseEntity<byte[]> esipa(@RequestBody byte[] body) {
        byte[] response = esipaAsn1Service.handle(body);
        return ResponseEntity.ok()
                .header(ADMIN_PROTOCOL_HEADER, ADMIN_PROTOCOL_VALUE)
                .header("Content-Type", ASN1_MEDIA_TYPE)
                .body(response);
    }

    @ExceptionHandler(UnsupportedEsipaFunctionException.class)
    public ResponseEntity<Void> handleUnsupported(UnsupportedEsipaFunctionException ex) {
        log.warn("Rejected unsupported ESipa function: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
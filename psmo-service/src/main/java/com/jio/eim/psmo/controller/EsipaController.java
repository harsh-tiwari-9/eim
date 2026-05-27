package com.jio.eim.psmo.controller;

import com.jio.eim.psmo.dto.EsipaNotification;
import com.jio.eim.psmo.dto.EsipaPollRequest;
import com.jio.eim.psmo.service.EsipaService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/esipa")
@RequiredArgsConstructor
public class EsipaController {

    private final EsipaService esipaService;

    @PostMapping(value = "/getEimPackage", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getEimPackage(@RequestBody EsipaPollRequest request) {
        Optional<byte[]> pkg = esipaService.getNextPackage(request.getEid(), request.getLastResults());
        return pkg
                .map(bytes -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(bytes))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/notifyResult")
    public ResponseEntity<Void> notifyResult(@RequestBody EsipaNotification notification) {
        esipaService.applyNotification(notification);
        return ResponseEntity.ok().build();
    }
}
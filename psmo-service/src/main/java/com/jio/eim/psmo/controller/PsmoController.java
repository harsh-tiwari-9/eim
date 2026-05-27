package com.jio.eim.psmo.controller;

import com.jio.eim.psmo.dto.ApiResponse;
import com.jio.eim.psmo.dto.PsmoOperationRequest;
import com.jio.eim.psmo.dto.PsmoOperationResponse;
import com.jio.eim.psmo.service.PsmoOperationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/psmo")
public class PsmoController {

    private final PsmoOperationService operationService;

    public PsmoController(PsmoOperationService operationService) {
        this.operationService = operationService;
    }

    @PostMapping("/operations")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PsmoOperationResponse>> submit(
        @Valid @RequestBody PsmoOperationRequest request,
        @RequestHeader("X-User-Email") String requestedBy
    ) {
        PsmoOperationResponse data = operationService.submit(request, requestedBy);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok("Operation Accepted", data));
    }

    @GetMapping("/operations/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ENGINEER','READ_ONLY','BSS_SYSTEM')")
    public ApiResponse<PsmoOperationResponse> get(@PathVariable long id) {
        return ApiResponse.ok("Operation retrieved", operationService.get(id));
    }
}
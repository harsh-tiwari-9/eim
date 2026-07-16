package com.jio.eim.psmo.controller;

import com.jio.eim.psmo.dto.ApiResponse;
import com.jio.eim.psmo.dto.PagedResponse;
import com.jio.eim.psmo.dto.PsmoOperationRequest;
import com.jio.eim.psmo.dto.PsmoOperationResponse;
import com.jio.eim.psmo.service.PsmoOperationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/psmo")
@Validated
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

    /**
     * Paginated operation history for the UI ops/logs page. All filters optional; newest first.
     * e.g. {@code GET /api/psmo/operations?page=0&size=20&status=EXECUTED&type=ENABLE&eid=...}.
     */
    @GetMapping("/operations")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ENGINEER','READ_ONLY','BSS_SYSTEM')")
    public ApiResponse<PagedResponse<PsmoOperationResponse>> list(
            @RequestParam(required = false) String eid,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.ok("Operations retrieved", operationService.list(eid, type, status, pageable));
    }
}
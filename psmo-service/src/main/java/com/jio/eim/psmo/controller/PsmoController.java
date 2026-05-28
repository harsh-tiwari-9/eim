package com.jio.eim.psmo.controller;

import com.jio.eim.psmo.dto.ApiResponse;
import com.jio.eim.psmo.dto.OperationLogResponse;
import com.jio.eim.psmo.dto.PagedLogsResponse;
import com.jio.eim.psmo.dto.PsmoOperationRequest;
import com.jio.eim.psmo.dto.PsmoOperationResponse;
import com.jio.eim.psmo.service.OperationLogService;
import com.jio.eim.psmo.service.PsmoOperationService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
public class PsmoController {

    private final PsmoOperationService operationService;
    private final OperationLogService logService;

    public PsmoController(PsmoOperationService operationService, OperationLogService logService) {
        this.operationService = operationService;
        this.logService = logService;
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

    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
    public ApiResponse<PagedLogsResponse> searchLog(
           @RequestParam(required = false) Long operationId,
           @RequestParam(required = false) String eventType,
           @RequestParam(required = false) String actor,
           @RequestParam(required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
           @RequestParam(required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
           @PageableDefault(size = 10, sort = "ts", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PagedLogsResponse data = logService.search(operationId, eventType, actor, from, to, pageable);
        return ApiResponse.ok("Logs retrieved", data);
    }

    @GetMapping("/operations/{id}/logs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN)")
    public ApiResponse<List<OperationLogResponse>> getLogsForOperation(
            @PathVariable("id") Long id
    ) {
        return ApiResponse.ok("Logs retrieved", logService.getForOperation(id));
    }
}
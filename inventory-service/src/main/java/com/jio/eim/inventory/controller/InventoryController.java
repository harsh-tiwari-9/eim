package com.jio.eim.inventory.controller;

import com.jio.eim.inventory.dto.ApiResponse;
import com.jio.eim.inventory.dto.InventoryListRequest;
import com.jio.eim.inventory.dto.InventoryRequest;
import com.jio.eim.inventory.dto.InventoryResponse;
import com.jio.eim.inventory.dto.PagedResponse;
import com.jio.eim.inventory.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.jio.eim.inventory.dto.IngestJobResponse;
import com.jio.eim.inventory.service.IngestJobQueryService;
import com.jio.eim.inventory.service.IngestUploadService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/inventory")
@Validated
public class InventoryController {

    private final InventoryService inventoryService;
    private final IngestUploadService ingestUploadService;
    private final IngestJobQueryService ingestJobQueryService;

    public InventoryController(
            InventoryService inventoryService,
            IngestUploadService ingestUploadService,
            IngestJobQueryService ingestJobQueryService) {
        this.inventoryService = inventoryService;
        this.ingestUploadService = ingestUploadService;
        this.ingestJobQueryService = ingestJobQueryService;
    }

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ENGINEER','READ_ONLY','BSS_SYSTEM')")
    public ApiResponse<IngestJobResponse> getJob(@PathVariable long jobId) {
        return ApiResponse.ok("Job retrieved", ingestJobQueryService.getJob(jobId));
    }

    @GetMapping("/jobs/{jobId}/download")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ENGINEER','BSS_SYSTEM')")
    public ResponseEntity<InputStreamResource> downloadJobResult(@PathVariable long jobId) {
        return ingestJobQueryService.downloadResult(jobId);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ENGINEER','BSS_SYSTEM')")
    public ResponseEntity<ApiResponse<IngestJobResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-User-Email") String uploadedBy) {
        IngestJobResponse data = ingestUploadService.upload(file, uploadedBy);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok("Upload accepted — job created", data));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ENGINEER','BSS_SYSTEM')")
    public ResponseEntity<ApiResponse<InventoryResponse>> register(
            @Valid @RequestBody InventoryRequest request) {
        InventoryResponse data = inventoryService.register(request);
        String message = data.getCertInfo() != null && data.getCertInfo().isChainValid()
                ? "Device registered — certificate chain valid"
                : "Device registered — certificate chain invalid";
        return ResponseEntity.ok(ApiResponse.ok(message, data));
    }

    @PostMapping("/list")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ENGINEER','READ_ONLY','BSS_SYSTEM')")
    public ApiResponse<PagedResponse<InventoryResponse>> list(
            @Valid @RequestBody(required = false) InventoryListRequest filters,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        InventoryListRequest req = filters != null ? filters : new InventoryListRequest();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "registeredAt"));
        return ApiResponse.ok("Devices retrieved", inventoryService.list(req.getOwnerId(), req.getStatus(), req.getEid(), req.getSearch(), pageable));
    }

    // @GetMapping("/{eid}")
    // @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ENGINEER','READ_ONLY','BSS_SYSTEM')")
    // public ApiResponse<InventoryResponse> get(
    //         @PathVariable @NotBlank @Pattern(regexp = "\\d{32}", message = "eid must be exactly 32 digits") String eid) {
    //     return ApiResponse.ok("Device retrieved", inventoryService.get(eid));
    // }

    @DeleteMapping("/{eid}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
    public ApiResponse<Void> delete(
            @PathVariable @NotBlank @Pattern(regexp = "\\d{32}", message = "eid must be exactly 32 digits") String eid) {
        inventoryService.delete(eid);
        return ApiResponse.ok("Device deleted", null);
    }
}

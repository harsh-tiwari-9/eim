package com.jio.eim.inventory.controller;

import com.jio.eim.inventory.dto.ApiResponse;
import com.jio.eim.inventory.dto.InventoryRequest;
import com.jio.eim.inventory.ingest.IngestJobStatus;
import com.jio.eim.inventory.dto.InventoryResponse;
import com.jio.eim.inventory.dto.PagedResponse;
import com.jio.eim.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @GetMapping("/jobs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ENGINEER','READ_ONLY','BSS_SYSTEM')")
    public ApiResponse<PagedResponse<IngestJobResponse>> listJobs(
            @RequestParam(required = false) IngestJobStatus status,
            @RequestParam(required = false) String uploadedBy,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ApiResponse.ok(
                "Jobs retrieved",
                ingestJobQueryService.listJobs(status, uploadedBy, pageable)
        );
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

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ENGINEER','READ_ONLY','BSS_SYSTEM')")
    public ApiResponse<PagedResponse<InventoryResponse>> list(
            @RequestParam(required = false) String ownerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "registeredAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ApiResponse.ok(
                "Devices retrieved", inventoryService.list(ownerId, status, search, pageable));
    }

    @GetMapping("/{eid}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ENGINEER','READ_ONLY','BSS_SYSTEM')")
    public ApiResponse<InventoryResponse> get(@PathVariable String eid) {
        return ApiResponse.ok("Device retrieved", inventoryService.get(eid));
    }

    @DeleteMapping("/{eid}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ENGINEER')")
    public ApiResponse<Void> delete(@PathVariable String eid) {
        inventoryService.delete(eid);
        return ApiResponse.ok("Device deleted", null);
    }
}

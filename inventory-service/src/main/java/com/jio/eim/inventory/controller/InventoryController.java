package com.jio.eim.inventory.controller;

import com.jio.eim.inventory.dto.ApiResponse;
import com.jio.eim.inventory.dto.InventoryRequest;
import com.jio.eim.inventory.dto.InventoryResponse;
import com.jio.eim.inventory.service.InventoryService;
import jakarta.validation.Valid;
import java.util.List;
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

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
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

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ENGINEER','READ_ONLY','BSS_SYSTEM')")
    public ApiResponse<List<InventoryResponse>> list(
            @RequestParam(required = false) String ownerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        return ApiResponse.ok("Devices retrieved", inventoryService.list(ownerId, status, search));
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

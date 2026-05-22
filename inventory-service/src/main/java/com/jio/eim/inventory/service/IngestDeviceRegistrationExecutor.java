package com.jio.eim.inventory.service;

import com.jio.eim.inventory.dto.InventoryRequest;
import com.jio.eim.inventory.ingest.RegisterResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Runs device registration in an isolated transaction so ingest row/job updates
 * can commit even when registration fails (e.g. duplicate profile).
 */
@Component
public class IngestDeviceRegistrationExecutor {

    private final InventoryService inventoryService;

    public IngestDeviceRegistrationExecutor(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RegisterResult register(InventoryRequest request) {
        if (inventoryService.deviceExists(request.getEid())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Device with EID already registered");
        }
        return inventoryService.registerInternal(request);
    }
}

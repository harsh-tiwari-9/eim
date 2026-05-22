package com.jio.eim.inventory.repository;

import com.jio.eim.inventory.entity.InventoryDevice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryDeviceRepository extends JpaRepository<InventoryDevice, String> {

    @Query("""
            SELECT d FROM InventoryDevice d
            WHERE (:ownerId IS NULL OR d.ownerId = :ownerId)
              AND (:status IS NULL OR d.status = :status)
              AND (:search IS NULL OR LOWER(d.eid) LIKE LOWER(CONCAT(CAST(:search AS string), '%')))
              AND d.status <> 'DELETED'
            """)
    Page<InventoryDevice> search(
            @Param("ownerId") String ownerId,
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable);
}
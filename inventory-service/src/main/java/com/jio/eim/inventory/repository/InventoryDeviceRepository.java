package com.jio.eim.inventory.repository;

import com.jio.eim.inventory.entity.InventoryDevice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryDeviceRepository extends JpaRepository<InventoryDevice, String> {

    @Query("""
            SELECT d FROM InventoryDevice d
            WHERE (:ownerId IS NULL OR d.ownerId = :ownerId)
              AND (:status IS NULL OR d.status = :status)
              AND (:search IS NULL OR LOWER(d.eid) LIKE LOWER(CONCAT(:search, '%')))
              AND d.status <> 'DELETED'
            """)
    List<InventoryDevice> search(
            @Param("ownerId") String ownerId,
            @Param("status") String status,
            @Param("search") String search);
}

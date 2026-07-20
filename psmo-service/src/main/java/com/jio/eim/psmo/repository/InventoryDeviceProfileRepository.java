package com.jio.eim.psmo.repository;

import com.jio.eim.psmo.entity.InventoryDeviceProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryDeviceProfileRepository extends JpaRepository<InventoryDeviceProfile, Long> {

    void deleteByEid(String eid);
}
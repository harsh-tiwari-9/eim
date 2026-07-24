package com.jio.eim.psmo.repository;

import com.jio.eim.psmo.entity.InventoryDeviceProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryDeviceProfileRepository extends JpaRepository<InventoryDeviceProfile, Long> {

    void deleteByEid(String eid);

    List<InventoryDeviceProfile> findByEid(String eid);

    Optional<InventoryDeviceProfile> findByEidAndIccid(String eid, String iccid);

    void deleteByEidAndIccid(String eid, String iccid);
}
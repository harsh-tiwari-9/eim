package com.jio.eim.inventory.repository;

import com.jio.eim.inventory.entity.DeviceProfile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceProfileRepository extends JpaRepository<DeviceProfile, Long> {

    List<DeviceProfile> findByEid(String eid);

    void deleteByEid(String eid);
}

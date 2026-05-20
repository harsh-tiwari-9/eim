package com.jio.eim.inventory.repository;

import com.jio.eim.inventory.entity.EuiccCert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EuiccCertRepository extends JpaRepository<EuiccCert, String> {
}

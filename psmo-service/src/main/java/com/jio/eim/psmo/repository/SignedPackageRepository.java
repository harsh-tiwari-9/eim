package com.jio.eim.psmo.repository;

import com.jio.eim.psmo.entity.SignedPackage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignedPackageRepository extends JpaRepository<SignedPackage, Long> {
}

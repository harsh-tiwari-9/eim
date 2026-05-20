package com.jio.eim.user.repository;

import com.jio.eim.user.entity.AuthEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthEventRepository extends JpaRepository<AuthEvent, Long> {
}

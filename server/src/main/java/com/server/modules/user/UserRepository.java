package com.server.modules.user;

import com.server.security.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUnitId(Long unitId);

    long countByRoleAndActiveTrue(Role role);
}

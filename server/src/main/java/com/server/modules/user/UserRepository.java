package com.server.modules.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** {@code phone} E.164 biçimindedir. */
    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);
}

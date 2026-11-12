package com.andrija.homesiloserver.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    long countByEnabledTrue();

    long countByRole(UserRole role);

    @Query("SELECT COALESCE(SUM(u.storageQuotaBytes), 0) FROM User u")
    long sumStorageQuotaBytes();

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.lastLoginAt = :at WHERE u.id = :id")
    void recordLogin(@Param("id") UUID id, @Param("at") LocalDateTime at);
}

package com.andrija.homesiloserver.user;

import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
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

    /** A new hash of the same password (an upgraded scheme); not a password change. */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.password = :hash WHERE u.id = :id")
    void updatePasswordHash(@Param("id") UUID id, @Param("hash") String hash);

    /** Locks the account's row, so uploads at the same moment can't both fit in the last of its quota. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.lastLoginAt = :at WHERE u.id = :id")
    void recordLogin(@Param("id") UUID id, @Param("at") LocalDateTime at);
}

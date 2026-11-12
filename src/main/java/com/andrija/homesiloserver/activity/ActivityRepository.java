package com.andrija.homesiloserver.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<ActivityEntry, UUID>, JpaSpecificationExecutor<ActivityEntry> {

    @Modifying
    @Query("DELETE FROM ActivityEntry a WHERE a.occurredAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}

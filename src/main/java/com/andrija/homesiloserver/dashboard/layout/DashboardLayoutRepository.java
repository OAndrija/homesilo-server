package com.andrija.homesiloserver.dashboard.layout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DashboardLayoutRepository extends JpaRepository<DashboardLayout, UUID> {
}

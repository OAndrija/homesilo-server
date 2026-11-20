package com.andrija.homesiloserver.settings;

import org.springframework.data.jpa.repository.JpaRepository;

interface ServerSettingRepository extends JpaRepository<ServerSetting, String> {
}

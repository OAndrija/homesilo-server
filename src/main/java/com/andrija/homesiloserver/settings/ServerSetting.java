package com.andrija.homesiloserver.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "server_settings")
@Getter
@Setter
@NoArgsConstructor
class ServerSetting {

    @Id
    @Column(name = "setting_key", length = 64)
    private String key;

    @Column(name = "setting_value", nullable = false)
    private String value;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private UUID updatedBy;
}

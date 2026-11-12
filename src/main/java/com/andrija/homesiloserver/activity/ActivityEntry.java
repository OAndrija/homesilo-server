package com.andrija.homesiloserver.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One line of the activity log. Names are copied in when it is written, so the
 * entry still reads correctly after the account, file or folder is gone.
 */
@Entity
@Table(name = "activity_log")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    /** Null for HomeSilo itself, or a sign-in attempt nobody is signed in for. */
    @Column(updatable = false)
    private UUID actorId;

    @Column(updatable = false)
    private String actorUsername;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false)
    private ActivityAction action;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(updatable = false)
    private TargetType targetType;

    @Column(updatable = false)
    private UUID targetId;

    @Column(updatable = false)
    private String targetName;

    @Column(updatable = false)
    private String oldValue;

    @Column(updatable = false)
    private String newValue;

    @Column(updatable = false)
    private String ipAddress;
}

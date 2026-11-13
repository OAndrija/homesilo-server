package com.andrija.homesiloserver.user.preferences;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Someone's settings, by name. One per account, created on the first change. */
@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor
public class UserPreferences {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @ElementCollection
    @CollectionTable(name = "user_preference_values", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "pref_key", length = 48)
    @Column(name = "pref_value", nullable = false, length = 64)
    private Map<String, String> values = new HashMap<>();

    public UserPreferences(UUID userId) {
        this.userId = userId;
    }
}

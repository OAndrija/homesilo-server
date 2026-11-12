package com.andrija.homesiloserver.dashboard.layout;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The tiles someone chose for their dashboard, in order. One per account, created on the first save. */
@Entity
@Table(name = "dashboard_layouts")
@Getter
@Setter
@NoArgsConstructor
public class DashboardLayout {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @ElementCollection
    @CollectionTable(name = "dashboard_layout_tiles", joinColumns = @JoinColumn(name = "user_id"))
    @OrderColumn(name = "position")
    private List<DashboardTile> tiles = new ArrayList<>();

    public DashboardLayout(UUID userId) {
        this.userId = userId;
    }
}

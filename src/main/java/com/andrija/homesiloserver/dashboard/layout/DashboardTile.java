package com.andrija.homesiloserver.dashboard.layout;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One tile on a dashboard: which one (the app's own id), how big and, optionally, which look. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTile {

    @Column(name = "tile_id", nullable = false, length = 40)
    private String tileId;

    @Column(name = "tile_size", nullable = false, length = 16)
    private String size;

    /** The tile's look when it has several; null for its default. */
    @Column(name = "tile_variant", length = 20)
    private String variant;
}

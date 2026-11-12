package com.andrija.homesiloserver.dashboard.layout.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Someone's dashboard tiles. {@code customized} is false (and the list empty) until
 * they first save a layout; the app then shows its default.
 */
public record DashboardLayoutResponse(boolean customized, List<TileDto> tiles, LocalDateTime updatedAt) {

    public static DashboardLayoutResponse notCustomized() {
        return new DashboardLayoutResponse(false, List.of(), null);
    }
}

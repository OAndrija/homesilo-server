package com.andrija.homesiloserver.dashboard.layout.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DashboardLayoutRequest(
        @NotNull(message = "Send the list of tiles, even if it's empty")
        @Size(max = DashboardLayoutRequest.MAX_TILES, message = "A dashboard can have at most 32 tiles")
        List<@Valid @NotNull TileDto> tiles
) {
    public static final int MAX_TILES = 32;
}

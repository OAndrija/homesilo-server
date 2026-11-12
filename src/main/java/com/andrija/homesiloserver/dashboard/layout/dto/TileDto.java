package com.andrija.homesiloserver.dashboard.layout.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * A tile as the app sends it. The server doesn't know the app's tiles, so it only
 * checks the id's shape; sizes are the ones the dashboard grid has (columns × rows):
 * small 1×1, tall 1×2, medium 2×1, large 2×2, wide (full width) and wide-tall (full width, two rows).
 */
public record TileDto(
        @NotNull(message = "Each tile needs an id")
        @Pattern(regexp = "[a-z][a-z0-9-]{0,39}", message = "Tile ids are lowercase letters, digits and dashes")
        String id,

        @NotNull(message = "Each tile needs a size")
        @Pattern(regexp = "small|tall|medium|large|wide|wide-tall",
                message = "Size must be small, tall, medium, large, wide or wide-tall")
        String size
) {
}

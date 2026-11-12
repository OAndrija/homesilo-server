package com.andrija.homesiloserver.dashboard.layout.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * A tile as the app sends it. The server doesn't know the app's tiles, so it only
 * checks the id's shape; sizes are the four the dashboard grid has.
 */
public record TileDto(
        @NotNull(message = "Each tile needs an id")
        @Pattern(regexp = "[a-z][a-z0-9-]{0,39}", message = "Tile ids are lowercase letters, digits and dashes")
        String id,

        @NotNull(message = "Each tile needs a size")
        @Pattern(regexp = "small|medium|large|wide", message = "Size must be small, medium, large or wide")
        String size
) {
}

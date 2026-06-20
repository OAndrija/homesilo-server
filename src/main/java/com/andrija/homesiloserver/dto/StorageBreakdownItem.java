package com.andrija.homesiloserver.dto;

public record StorageBreakdownItem(
        String category,
        long bytes
) {
}

package com.andrija.homesiloserver.common.web;

import org.springframework.data.domain.Page;

import java.util.List;

/** Stable JSON shape for paginated results (Spring's {@code Page} serialization is not a stable contract). */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}

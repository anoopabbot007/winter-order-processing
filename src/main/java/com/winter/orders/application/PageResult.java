package com.winter.orders.application;

import java.util.List;

/** Pagination metadata and application results, independent of API response DTOs. */
public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {
}

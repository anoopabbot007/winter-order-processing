package com.winter.orders.application;

import com.winter.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Lightweight application-level order representation for list operations. */
public record OrderSummaryResult(
        UUID id,
        String customerReference,
        BigDecimal totalAmount,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt) {
}

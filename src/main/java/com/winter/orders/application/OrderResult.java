package com.winter.orders.application;

import com.winter.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Application-level order details returned to inbound adapters. */
public record OrderResult(
        UUID id,
        String customerReference,
        List<OrderItemResult> items,
        BigDecimal totalAmount,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt) {
}

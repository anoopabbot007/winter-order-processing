package com.winter.orders.application;

import java.math.BigDecimal;
import java.util.UUID;

/** Application-level representation of an order line, independent of the HTTP API. */
public record OrderItemResult(UUID id, String itemName, int quantity, BigDecimal unitPrice) {
}

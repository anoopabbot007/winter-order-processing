package com.winter.orders.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(UUID id, String itemName, int quantity, BigDecimal unitPrice) {
}

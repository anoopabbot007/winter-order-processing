package com.winter.orders.api.dto;

import com.winter.orders.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(@NotNull OrderStatus status) {
}

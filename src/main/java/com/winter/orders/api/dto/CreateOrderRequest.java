package com.winter.orders.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank @Size(max = 100) String customerReference,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid CreateOrderItemRequest> items) {
}

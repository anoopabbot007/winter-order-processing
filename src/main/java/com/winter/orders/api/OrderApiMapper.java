package com.winter.orders.api;

import com.winter.orders.api.dto.CreateOrderItemRequest;
import com.winter.orders.api.dto.CreateOrderRequest;
import com.winter.orders.api.dto.OrderItemResponse;
import com.winter.orders.api.dto.OrderResponse;
import com.winter.orders.api.dto.OrderSummaryResponse;
import com.winter.orders.api.dto.PageResponse;
import com.winter.orders.application.CreateOrderCommand;
import com.winter.orders.persistence.OrderEntity;
import com.winter.orders.persistence.OrderItemEntity;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Keeps external request/response shapes separate from the application and JPA models. */
@Component
public class OrderApiMapper {

    public CreateOrderCommand toCommand(CreateOrderRequest request) {
        return new CreateOrderCommand(
                request.customerReference(),
                request.items().stream()
                        .map(this::toCommandLine)
                        .toList());
    }

    public OrderResponse toResponse(OrderEntity order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerReference(),
                order.getItems().stream().map(this::toItemResponse).toList(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    public OrderSummaryResponse toSummary(OrderEntity order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getCustomerReference(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    public <T, R> PageResponse<R> toPage(Page<T> page, Function<T, R> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private CreateOrderCommand.Line toCommandLine(CreateOrderItemRequest request) {
        return new CreateOrderCommand.Line(request.itemName(), request.quantity(), request.unitPrice());
    }

    private OrderItemResponse toItemResponse(OrderItemEntity item) {
        return new OrderItemResponse(item.getId(), item.getItemName(), item.getQuantity(), item.getUnitPrice());
    }
}

package com.winter.orders.api;

import com.winter.orders.api.dto.CreateOrderItemRequest;
import com.winter.orders.api.dto.CreateOrderRequest;
import com.winter.orders.api.dto.OrderItemResponse;
import com.winter.orders.api.dto.OrderResponse;
import com.winter.orders.api.dto.OrderSummaryResponse;
import com.winter.orders.api.dto.PageResponse;
import com.winter.orders.application.CreateOrderCommand;
import com.winter.orders.application.OrderItemResult;
import com.winter.orders.application.OrderResult;
import com.winter.orders.application.OrderSummaryResult;
import com.winter.orders.application.PageResult;
import java.util.function.Function;
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

    public OrderResponse toResponse(OrderResult order) {
        return new OrderResponse(
                order.id(),
                order.customerReference(),
                order.items().stream().map(this::toItemResponse).toList(),
                order.totalAmount(),
                order.status(),
                order.createdAt(),
                order.updatedAt());
    }

    public OrderSummaryResponse toSummary(OrderSummaryResult order) {
        return new OrderSummaryResponse(
                order.id(),
                order.customerReference(),
                order.totalAmount(),
                order.status(),
                order.createdAt(),
                order.updatedAt());
    }

    public <T, R> PageResponse<R> toPage(PageResult<T> page, Function<T, R> mapper) {
        return new PageResponse<>(
                page.content().stream().map(mapper).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                page.first(),
                page.last());
    }

    private CreateOrderCommand.Line toCommandLine(CreateOrderItemRequest request) {
        return new CreateOrderCommand.Line(request.itemName(), request.quantity(), request.unitPrice());
    }

    private OrderItemResponse toItemResponse(OrderItemResult item) {
        return new OrderItemResponse(item.id(), item.itemName(), item.quantity(), item.unitPrice());
    }
}

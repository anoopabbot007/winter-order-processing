package com.winter.orders.application;

import com.winter.orders.persistence.OrderEntity;
import com.winter.orders.persistence.OrderItemEntity;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Converts persistence aggregates into application results without exposing API DTOs. */
@Component
public class OrderResultMapper {

    public OrderResult toOrderResult(OrderEntity order) {
        return new OrderResult(
                order.getId(),
                order.getCustomerReference(),
                order.getItems().stream().map(this::toItemResult).toList(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    public OrderSummaryResult toSummaryResult(OrderEntity order) {
        return new OrderSummaryResult(
                order.getId(),
                order.getCustomerReference(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    public PageResult<OrderSummaryResult> toSummaryPage(Page<OrderEntity> page) {
        return new PageResult<>(
                page.getContent().stream().map(this::toSummaryResult).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private OrderItemResult toItemResult(OrderItemEntity item) {
        return new OrderItemResult(item.getId(), item.getItemName(), item.getQuantity(), item.getUnitPrice());
    }
}

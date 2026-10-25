package com.winter.orders.domain;

import java.util.Map;
import org.springframework.stereotype.Component;

/** Owns the complete state-transition rule set so callers cannot bypass it. */
@Component
public class OrderStatusTransitionPolicy {

    private static final Map<OrderStatus, OrderStatus> FORWARD_TRANSITIONS = Map.of(
            OrderStatus.PENDING, OrderStatus.PROCESSING,
            OrderStatus.PROCESSING, OrderStatus.SHIPPED,
            OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    public void requireForwardTransition(OrderStatus current, OrderStatus requested) {
        if (FORWARD_TRANSITIONS.get(current) != requested) {
            throw new InvalidOrderTransitionException(current, requested);
        }
    }

    public void requireCancellable(OrderStatus current) {
        if (current != OrderStatus.PENDING) {
            throw new OrderConflictException(
                    "Only a PENDING order can be cancelled; current status is %s.".formatted(current));
        }
    }
}

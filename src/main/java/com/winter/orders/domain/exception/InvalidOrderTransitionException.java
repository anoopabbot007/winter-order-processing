package com.winter.orders.domain.exception;

import com.winter.orders.domain.OrderStatus;

public class InvalidOrderTransitionException extends OrderConflictException {

    public InvalidOrderTransitionException(OrderStatus current, OrderStatus requested) {
        super("Order cannot move from %s to %s.".formatted(current, requested));
    }
}

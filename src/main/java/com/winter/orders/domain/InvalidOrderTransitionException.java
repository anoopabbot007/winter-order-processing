package com.winter.orders.domain;

public class InvalidOrderTransitionException extends OrderConflictException {

    public InvalidOrderTransitionException(OrderStatus current, OrderStatus requested) {
        super("Order cannot move from %s to %s.".formatted(current, requested));
    }
}

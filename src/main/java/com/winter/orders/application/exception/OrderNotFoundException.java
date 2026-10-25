package com.winter.orders.application.exception;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID orderId) {
        super("Order %s was not found.".formatted(orderId));
    }
}

package com.winter.orders.application;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderCommand(String customerReference, List<Line> items) {

    public record Line(String itemName, int quantity, BigDecimal unitPrice) {
    }
}

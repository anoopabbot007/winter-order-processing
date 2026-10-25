package com.winter.orders.domain;

import com.winter.orders.domain.exception.InvalidOrderTransitionException;
import com.winter.orders.domain.exception.OrderConflictException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrderStatusTransitionPolicyTest {

    private final OrderStatusTransitionPolicy policy = new OrderStatusTransitionPolicy();

    @Test
    void acceptsEachRequiredForwardTransition() {
        assertThatCode(() -> policy.requireForwardTransition(OrderStatus.PENDING, OrderStatus.PROCESSING))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.requireForwardTransition(OrderStatus.PROCESSING, OrderStatus.SHIPPED))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.requireForwardTransition(OrderStatus.SHIPPED, OrderStatus.DELIVERED))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsBackwardRepeatedAndCancellationTransitions() {
        assertThatThrownBy(() -> policy.requireForwardTransition(OrderStatus.DELIVERED, OrderStatus.SHIPPED))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThatThrownBy(() -> policy.requireForwardTransition(OrderStatus.PENDING, OrderStatus.PENDING))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThatThrownBy(() -> policy.requireForwardTransition(OrderStatus.PENDING, OrderStatus.CANCELLED))
                .isInstanceOf(InvalidOrderTransitionException.class);
    }

    @Test
    void cancellationIsAllowedOnlyWhilePending() {
        assertThatCode(() -> policy.requireCancellable(OrderStatus.PENDING)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireCancellable(OrderStatus.PROCESSING))
                .isInstanceOf(OrderConflictException.class);
        assertThatThrownBy(() -> policy.requireCancellable(OrderStatus.CANCELLED))
                .isInstanceOf(OrderConflictException.class);
    }
}

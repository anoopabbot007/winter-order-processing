package com.winter.orders.application;

import com.winter.orders.domain.OrderStatus;
import com.winter.orders.domain.OrderStatusTransitionPolicy;
import com.winter.orders.application.exception.OrderNotFoundException;
import com.winter.orders.persistence.OrderEntity;
import com.winter.orders.persistence.OrderRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderStatusService {

    private final OrderRepository orderRepository;
    private final OrderStatusTransitionPolicy transitionPolicy;
    private final OrderResultMapper mapper;
    private final Clock clock;

    public OrderStatusService(
            OrderRepository orderRepository,
            OrderStatusTransitionPolicy transitionPolicy,
            OrderResultMapper mapper,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.transitionPolicy = transitionPolicy;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public OrderResult transition(UUID orderId, OrderStatus requestedStatus) {
        OrderEntity order = lockOrder(orderId);
        transitionPolicy.requireForwardTransition(order.getStatus(), requestedStatus);
        order.changeStatus(requestedStatus, clock.instant());
        return mapper.toOrderResult(orderRepository.findDetailsById(orderId).orElseThrow());
    }

    @Transactional
    public OrderResult cancel(UUID orderId) {
        OrderEntity order = lockOrder(orderId);
        transitionPolicy.requireCancellable(order.getStatus());
        order.changeStatus(OrderStatus.CANCELLED, clock.instant());
        return mapper.toOrderResult(orderRepository.findDetailsById(orderId).orElseThrow());
    }

    private OrderEntity lockOrder(UUID orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}

package com.winter.orders.application;

import com.winter.orders.domain.OrderStatus;
import com.winter.orders.domain.OrderStatusTransitionPolicy;
import com.winter.orders.persistence.OrderRepository;
import java.time.Clock;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PendingOrderProcessor {

    private final OrderRepository orderRepository;
    private final OrderStatusTransitionPolicy transitionPolicy;
    private final Clock clock;

    public PendingOrderProcessor(
            OrderRepository orderRepository,
            OrderStatusTransitionPolicy transitionPolicy,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.transitionPolicy = transitionPolicy;
        this.clock = clock;
    }

    /** Processes at most one bounded batch in one transaction; also callable directly by tests. */
    @Transactional
    public int processNextBatch(int batchSize) {
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("Batch size must be between 1 and 1000.");
        }

        var pageable = PageRequest.of(0, batchSize, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
        var pending = orderRepository.findNextBatchForUpdate(OrderStatus.PENDING, pageable);
        var now = clock.instant();
        pending.forEach(order -> {
            transitionPolicy.requireForwardTransition(order.getStatus(), OrderStatus.PROCESSING);
            order.changeStatus(OrderStatus.PROCESSING, now);
        });
        return pending.size();
    }
}

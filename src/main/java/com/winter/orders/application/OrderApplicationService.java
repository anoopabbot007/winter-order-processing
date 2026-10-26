package com.winter.orders.application;

import com.winter.orders.application.exception.OrderNotFoundException;
import com.winter.orders.domain.OrderStatus;
import com.winter.orders.persistence.OrderEntity;
import com.winter.orders.persistence.OrderItemEntity;
import com.winter.orders.persistence.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderApplicationService {

    private final OrderRepository orderRepository;
    private final OrderResultMapper mapper;
    private final Clock clock;

    public OrderApplicationService(OrderRepository orderRepository, OrderResultMapper mapper, Clock clock) {
        this.orderRepository = orderRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public OrderResult create(CreateOrderCommand command) {
        if (command.items() == null || command.items().isEmpty()) {
            throw new IllegalArgumentException("An order must contain at least one item.");
        }

        BigDecimal total = command.items().stream()
                .map(line -> line.unitPrice()
                        .setScale(2, RoundingMode.UNNECESSARY)
                        .multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        var now = clock.instant();
        OrderEntity order = OrderEntity.create(UUID.randomUUID(), command.customerReference(), total, now);
        command.items().forEach(line -> order.addItem(new OrderItemEntity(
                UUID.randomUUID(),
                line.itemName(),
                line.quantity(),
                line.unitPrice().setScale(2, RoundingMode.UNNECESSARY))));

        return mapper.toOrderResult(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResult get(UUID orderId) {
        OrderEntity order = orderRepository.findDetailsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return mapper.toOrderResult(order);
    }

    @Transactional(readOnly = true)
    public PageResult<OrderSummaryResult> list(OrderStatus status, Pageable pageable) {
        var orders = status == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findAll((root, query, criteriaBuilder) ->
                        criteriaBuilder.equal(root.get("status"), status), pageable);
        return mapper.toSummaryPage(orders);
    }
}

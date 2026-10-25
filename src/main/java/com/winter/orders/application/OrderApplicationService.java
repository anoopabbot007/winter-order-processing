package com.winter.orders.application;

import com.winter.orders.api.OrderApiMapper;
import com.winter.orders.api.dto.OrderResponse;
import com.winter.orders.api.dto.OrderSummaryResponse;
import com.winter.orders.api.dto.PageResponse;
import com.winter.orders.application.exception.OrderNotFoundException;
import com.winter.orders.domain.OrderStatus;
import com.winter.orders.persistence.OrderEntity;
import com.winter.orders.persistence.OrderItemEntity;
import com.winter.orders.persistence.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderApplicationService {

    private final OrderRepository orderRepository;
    private final OrderApiMapper mapper;
    private final Clock clock;

    public OrderApplicationService(OrderRepository orderRepository, OrderApiMapper mapper, Clock clock) {
        this.orderRepository = orderRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse create(CreateOrderCommand command) {
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

        return mapper.toResponse(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID orderId) {
        OrderEntity order = orderRepository.findDetailsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return mapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> list(OrderStatus status, Pageable pageable) {
        Page<OrderEntity> orders = status == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findAll((root, query, criteriaBuilder) ->
                        criteriaBuilder.equal(root.get("status"), status), pageable);
        return mapper.toPage(orders, mapper::toSummary);
    }
}

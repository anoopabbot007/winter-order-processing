package com.winter.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.winter.orders.api.OrderApiMapper;
import com.winter.orders.domain.OrderStatus;
import com.winter.orders.persistence.OrderEntity;
import com.winter.orders.persistence.OrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");

    @Mock
    private OrderRepository orderRepository;

    private OrderApplicationService service;

    @BeforeEach
    void setUp() {
        service = new OrderApplicationService(
                orderRepository,
                new OrderApiMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsMultipleItemsWithServerCalculatedTotalAndPendingStatus() {
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var command = new CreateOrderCommand("customer-42", List.of(
                new CreateOrderCommand.Line("Desk lamp", 2, new BigDecimal("12.25")),
                new CreateOrderCommand.Line("Cable", 3, new BigDecimal("1.50"))));

        var response = service.create(command);

        assertThat(response.id()).isNotNull();
        assertThat(response.customerReference()).isEqualTo("customer-42");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.totalAmount()).isEqualByComparingTo("29.00");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).extracting("unitPrice")
                .containsExactly(new BigDecimal("12.25"), new BigDecimal("1.50"));
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsAnEmptyCommandEvenOutsideTheHttpValidationBoundary() {
        assertThatThrownBy(() -> service.create(new CreateOrderCommand("customer-42", List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one item");
    }
}

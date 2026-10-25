package com.winter.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.winter.orders.domain.OrderStatus;
import com.winter.orders.domain.OrderStatusTransitionPolicy;
import com.winter.orders.persistence.OrderEntity;
import com.winter.orders.persistence.OrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PendingOrderProcessorTest {

    private static final Instant NOW = Instant.parse("2026-02-03T04:05:06Z");

    @Mock
    private OrderRepository repository;

    @Test
    void processesOnlyPendingRowsInABoundedBatch() {
        OrderEntity first = OrderEntity.create(UUID.randomUUID(), "c1", new BigDecimal("2.00"), NOW.minusSeconds(5));
        OrderEntity second = OrderEntity.create(UUID.randomUUID(), "c2", new BigDecimal("3.00"), NOW.minusSeconds(2));
        when(repository.findNextBatchForUpdate(eq(OrderStatus.PENDING), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(first, second));
        var processor = new PendingOrderProcessor(
                repository,
                new OrderStatusTransitionPolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        int count = processor.processNextBatch(2);

        assertThat(count).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(second.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(first.getUpdatedAt()).isEqualTo(NOW);
        verify(repository).findNextBatchForUpdate(eq(OrderStatus.PENDING), org.mockito.ArgumentMatchers.argThat(
                page -> page.getPageSize() == 2 && page.getSort().isSorted()));
    }

    @Test
    void returnsZeroWhenThereAreNoPendingRowsAndEnforcesMaximumBatchSize() {
        when(repository.findNextBatchForUpdate(eq(OrderStatus.PENDING), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of());
        var processor = new PendingOrderProcessor(repository, new OrderStatusTransitionPolicy(), Clock.systemUTC());

        assertThat(processor.processNextBatch(100)).isZero();
        verify(repository).findNextBatchForUpdate(eq(OrderStatus.PENDING), org.mockito.ArgumentMatchers.any(Pageable.class));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> processor.processNextBatch(1_001))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

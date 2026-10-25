package com.winter.orders.persistence;

import com.winter.orders.domain.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_status_created_at", columnList = "status, created_at, id"),
        @Index(name = "idx_orders_created_at", columnList = "created_at")
})
public class OrderEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_reference", nullable = false, length = 100)
    private String customerReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItemEntity> items = new ArrayList<>();

    protected OrderEntity() {
    }

    private OrderEntity(UUID id, String customerReference, BigDecimal totalAmount, Instant now) {
        this.id = id;
        this.customerReference = customerReference;
        this.status = OrderStatus.PENDING;
        this.totalAmount = totalAmount;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static OrderEntity create(UUID id, String customerReference, BigDecimal totalAmount, Instant now) {
        return new OrderEntity(id, customerReference, totalAmount, now);
    }

    public void addItem(OrderItemEntity item) {
        items.add(item);
        item.attachTo(this);
    }

    public void changeStatus(OrderStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerReference() {
        return customerReference;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItemEntity> getItems() {
        return List.copyOf(items);
    }
}

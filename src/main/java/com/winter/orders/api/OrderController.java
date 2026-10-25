package com.winter.orders.api;

import com.winter.orders.api.dto.CreateOrderRequest;
import com.winter.orders.api.dto.OrderResponse;
import com.winter.orders.api.dto.OrderSummaryResponse;
import com.winter.orders.api.dto.PageResponse;
import com.winter.orders.api.dto.StatusUpdateRequest;
import com.winter.orders.application.OrderApplicationService;
import com.winter.orders.application.OrderStatusService;
import com.winter.orders.domain.OrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/orders")
@Validated
@Tag(name = "Orders", description = "Create and manage customer orders")
public class OrderController {

    private final OrderApplicationService orderService;
    private final OrderStatusService statusService;
    private final OrderApiMapper mapper;

    public OrderController(OrderApplicationService orderService, OrderStatusService statusService, OrderApiMapper mapper) {
        this.orderService = orderService;
        this.statusService = statusService;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Create an order", description = "The server calculates the total and snapshots the supplied unit prices.")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = orderService.create(mapper.toCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{orderId}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order details")
    public OrderResponse get(@PathVariable UUID orderId) {
        return orderService.get(orderId);
    }

    @GetMapping
    @Operation(summary = "List orders", description = "Returns paged order summaries sorted by creation time.")
    public PageResponse<OrderSummaryResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Sort sortOrder = parseSort(sort);
        return orderService.list(status, PageRequest.of(page, size, sortOrder));
    }

    @PatchMapping("/{orderId}/status")
    @Operation(
            summary = "Advance order status",
            description = "Administrative operation. Authentication is not configured. Only PENDING→PROCESSING→SHIPPED→DELIVERED is allowed; use the cancel endpoint for cancellation.")
    public OrderResponse updateStatus(@PathVariable UUID orderId, @Valid @RequestBody StatusUpdateRequest request) {
        return statusService.transition(orderId, request.status());
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(
            summary = "Cancel a pending order",
            description = "Administrative operation. Cancellation is accepted only while the order is PENDING; authentication is not configured.")
    public OrderResponse cancel(@PathVariable UUID orderId) {
        return statusService.cancel(orderId);
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",", -1);
        if (parts.length != 2 || !"createdAt".equals(parts[0])) {
            throw new InvalidSortParameterException();
        }
        try {
            return Sort.by(Sort.Direction.fromString(parts[1]), "createdAt");
        } catch (IllegalArgumentException exception) {
            throw new InvalidSortParameterException();
        }
    }
}

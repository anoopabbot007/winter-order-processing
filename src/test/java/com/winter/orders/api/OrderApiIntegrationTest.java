package com.winter.orders.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.orders.application.OrderStatusService;
import com.winter.orders.application.PendingOrderProcessor;
import com.winter.orders.domain.exception.OrderConflictException;
import com.winter.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OrderApiIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PendingOrderProcessor pendingOrderProcessor;

    @Autowired
    private OrderStatusService orderStatusService;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE order_items, orders CASCADE");
    }

    @Test
    void createsAndRetrievesAnOrderWithServerCalculatedTotalAndPriceSnapshots() throws Exception {
        String body = """
                {
                  "customerReference": "customer-100",
                  "totalAmount": 0.01,
                  "items": [
                    {"itemName": "Desk lamp", "quantity": 2, "unitPrice": 12.25},
                    {"itemName": "Cable", "quantity": 3, "unitPrice": 1.50}
                  ]
                }
                """;

        var created = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerReference").value("customer-100"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(29.0))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();

        JsonNode createdBody = objectMapper.readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String orderId = createdBody.get("id").asText();
        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice").value(12.25))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        assertThat(jdbcTemplate.queryForObject("select total_amount from orders where id = ?", BigDecimal.class, UUID.fromString(orderId)))
                .isEqualByComparingTo("29.00");
    }

    @Test
    void returnsNotFoundForUnknownOrderAndUsesProblemDetails() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{orderId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void exposesActuatorHealthWithoutDatabaseDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void rejectsEmptyItemsInvalidQuantityAndNegativePriceWithConsistentErrors() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerReference\":\"customer-1\",\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.items").exists());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerReference\":\"customer-1\",\"items\":[{\"itemName\":\"x\",\"quantity\":0,\"unitPrice\":-1.00}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerReference\":\"customer-1\",\"items\":[null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void listsWithPaginationAndOptionalStatusFilter() throws Exception {
        String pendingId = createOrder("customer-pending");
        String processingId = createOrder("customer-processing");
        createOrder("customer-pending-2");
        mockMvc.perform(patch("/api/v1/orders/{id}/status", processingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders")
                        .param("status", "PENDING")
                        .param("page", "0")
                        .param("size", "1")
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/v1/orders").param("status", "PROCESSING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(processingId));
        assertThat(pendingId).isNotBlank();
    }

    @Test
    void listsAllOrdersWithDatabasePagination() throws Exception {
        createOrder("customer-page-one");
        createOrder("customer-page-two");

        mockMvc.perform(get("/api/v1/orders")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void enforcesForwardTransitionsAndRejectsBackwardsTransitions() throws Exception {
        String orderId = createOrder("customer-lifecycle");
        patchStatus(orderId, "PROCESSING", 200);
        patchStatus(orderId, "SHIPPED", 200);
        patchStatus(orderId, "DELIVERED", 200);
        patchStatus(orderId, "PROCESSING", 409);
        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void cancelsPendingOrdersAndScheduledProcessorLeavesCancelledOrdersAlone() throws Exception {
        String pending = createOrder("customer-to-process");
        String cancelled = createOrder("customer-to-cancel");

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", cancelled))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(pendingOrderProcessor.processNextBatch(100)).isEqualTo(1);
        mockMvc.perform(get("/api/v1/orders/{id}", pending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
        mockMvc.perform(get("/api/v1/orders/{id}", cancelled))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void rejectsCancellationAfterProcessingHasStarted() throws Exception {
        String orderId = createOrder("customer-no-cancel");
        patchStatus(orderId, "PROCESSING", 200);
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CONFLICT"));
    }

    @Test
    void concurrentCancellationAndProcessingAllowOnlyOnePendingTransition() throws Exception {
        String orderId = createOrder("customer-race");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> cancellation = executor.submit(() -> runRace(start, () -> orderStatusService.cancel(UUID.fromString(orderId))));
            Future<Boolean> processing = executor.submit(() -> runRace(start, () -> orderStatusService.transition(
                    UUID.fromString(orderId), OrderStatus.PROCESSING)));
            start.countDown();

            List<Boolean> outcomes = List.of(cancellation.get(), processing.get());
            assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        }

        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is("CANCELLED"), org.hamcrest.Matchers.is("PROCESSING"))));
    }

    private String createOrder(String customerReference) throws Exception {
        String body = """
                {"customerReference":"%s","items":[{"itemName":"Sample","quantity":1,"unitPrice":4.20}]}
                """.formatted(customerReference);
        String response = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).get("id").asText();
    }

    private void patchStatus(String orderId, String status, int expectedStatus) throws Exception {
        mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"%s\"}".formatted(status)))
                .andExpect(status().is(expectedStatus));
    }

    private boolean runRace(CountDownLatch start, ThrowingRunnable operation) throws Exception {
        start.await();
        try {
            operation.run();
            return true;
        } catch (OrderConflictException | PessimisticLockingFailureException conflict) {
            return false;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}

package com.winter.orders.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Winter Order Processing API",
        version = "v1",
        description = "Transactional order creation, retrieval, listing, status changes, and cancellation."))
public class OpenApiConfiguration {
}

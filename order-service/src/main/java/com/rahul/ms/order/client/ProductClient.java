package com.rahul.ms.order.client;

import com.rahul.ms.order.dto.ProductResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class ProductClient {

    private static final Logger log = LoggerFactory.getLogger(ProductClient.class);
    private final RestClient restClient;

    public ProductClient(RestClient.Builder restClientBuilder,
                         @Value("${product.service.url:http://product-service}") String productServiceUrl) {
        this.restClient = restClientBuilder
                .baseUrl(productServiceUrl)
                .build();
    }

    @CircuitBreaker(name = "productService", fallbackMethod = "getProductFallback")
    @Retry(name = "productService")
    public Optional<ProductResponse> getProductById(String productId) {
        log.info("Calling product-service via Eureka for productId: {}", productId);
        ProductResponse response = restClient.get()
                .uri("/api/products/{id}", productId)
                .retrieve()
                .body(ProductResponse.class);
        return Optional.ofNullable(response);
    }

    public Optional<ProductResponse> getProductFallback(String productId, Throwable throwable) {
        log.error("Fallback triggered for productId: {}. Downstream unavailable: {}", productId, throwable.getMessage());
        return Optional.empty();
    }
}
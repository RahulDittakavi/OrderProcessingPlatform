package com.rahul.ms.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.rahul.ms.order.entity.Order;
import com.rahul.ms.order.entity.OrderStatus;
import com.rahul.ms.order.repository.OrderRepository;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=validate",
        "order.events.publisher.initial-delay=60000"
})
class OrderPostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void appliesMigrationsAndPersistsOrdersWithThePostgresSchema() {
        Integer appliedMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1', '2') AND success",
                Integer.class);
        Integer skuColumnCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_name = 'orders' AND column_name = 'product_sku_code'
                """,
                Integer.class);
        assertEquals(2, appliedMigrations);
        assertEquals(1, skuColumnCount);

        Order savedOrder = orderRepository.saveAndFlush(Order.builder()
                .idempotencyKey("postgres-integration-test")
                .orderNumber("postgres-integration-order")
                .productId("product-123")
                .productSkuCode("sku-456")
                .quantity(2)
                .price(new BigDecimal("99.98"))
                .status(OrderStatus.PENDING)
                .build());

        Order reloadedOrder = orderRepository.findById(savedOrder.getId()).orElseThrow();
        assertNotNull(reloadedOrder.getId());
        assertEquals("sku-456", reloadedOrder.getProductSkuCode());
        assertEquals(new BigDecimal("99.98"), reloadedOrder.getPrice());
    }
}

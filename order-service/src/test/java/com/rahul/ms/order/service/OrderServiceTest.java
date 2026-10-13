package com.rahul.ms.order.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rahul.ms.order.exception.IdempotencyKeyConflictException;
import com.rahul.ms.order.client.InventoryClient;
import com.rahul.ms.order.client.ProductClient;
import com.rahul.ms.order.dto.OrderRequest;
import com.rahul.ms.order.dto.OrderResponse;
import com.rahul.ms.order.dto.ProductResponse;
import com.rahul.ms.order.entity.Order;
import com.rahul.ms.order.entity.OrderStatus;
import com.rahul.ms.order.repository.OrderRepository;
import org.mockito.InOrder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductClient productClient;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private OrderPersistenceService orderPersistenceService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void persistsPendingBeforeReservationAndConfirmsAfterSuccess() {
                List<OrderStatus> savedStatuses = new ArrayList<>();
                List<String> savedOrderNumbers = new ArrayList<>();
        when(productClient.getProductById("product-123")).thenReturn(Optional.of(
                new ProductResponse("product-123", "sku-456", "Keyboard", "Mechanical keyboard",
                        new BigDecimal("49.99"))));
                when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                        Order savedOrder = invocation.getArgument(0);
                        savedStatuses.add(savedOrder.getStatus());
                        savedOrderNumbers.add(savedOrder.getOrderNumber());
                        return savedOrder;
                });
        when(orderPersistenceService.confirmAndEnqueue(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setStatus(OrderStatus.CONFIRMED);
            savedStatuses.add(OrderStatus.CONFIRMED);
            return order;
        });

        OrderResponse response = orderService.placeOrder(new OrderRequest("product-123", 2, null), "request-123");

        InOrder sequence = inOrder(orderRepository, inventoryClient);
        sequence.verify(orderRepository).save(any(Order.class));
        sequence.verify(inventoryClient).reduceStock(eq(savedOrderNumbers.get(0)), eq("sku-456"), eq(2));
        verify(orderPersistenceService).confirmAndEnqueue(any(Order.class));
        assertEquals(List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED), savedStatuses);
        assertEquals(OrderStatus.CONFIRMED, response.status());
    }

    @Test
    void marksOrderRejectedWhenInventoryExplicitlyRejectsReservation() {
                List<OrderStatus> savedStatuses = new ArrayList<>();
        when(productClient.getProductById("product-123")).thenReturn(Optional.of(
                new ProductResponse("product-123", "sku-456", "Keyboard", "Mechanical keyboard",
                        new BigDecimal("49.99"))));
                when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                        Order savedOrder = invocation.getArgument(0);
                        savedStatuses.add(savedOrder.getStatus());
                        return savedOrder;
                });
        doThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
                new byte[0], StandardCharsets.UTF_8)).when(inventoryClient)
                .reduceStock(any(String.class), org.mockito.ArgumentMatchers.eq("sku-456"),
                        org.mockito.ArgumentMatchers.eq(2));

        assertThrows(IllegalArgumentException.class,
                () -> orderService.placeOrder(new OrderRequest("product-123", 2, null), "request-123"));

        InOrder sequence = inOrder(orderRepository, inventoryClient);
        sequence.verify(orderRepository).save(any(Order.class));
        sequence.verify(inventoryClient).reduceStock(any(String.class), org.mockito.ArgumentMatchers.eq("sku-456"),
                org.mockito.ArgumentMatchers.eq(2));
        sequence.verify(orderRepository).save(any(Order.class));
        assertEquals(List.of(OrderStatus.PENDING, OrderStatus.REJECTED), savedStatuses);
        verifyNoInteractions(orderPersistenceService);
    }

    @Test
    void leavesOrderPendingWhenReservationOutcomeIsUnknown() {
                List<OrderStatus> savedStatuses = new ArrayList<>();
        when(productClient.getProductById("product-123")).thenReturn(Optional.of(
                new ProductResponse("product-123", "sku-456", "Keyboard", "Mechanical keyboard",
                        new BigDecimal("49.99"))));
                when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                        Order savedOrder = invocation.getArgument(0);
                        savedStatuses.add(savedOrder.getStatus());
                        return savedOrder;
                });
        doThrow(new IllegalStateException("Inventory request timed out"))
                .when(inventoryClient).reduceStock(any(String.class), org.mockito.ArgumentMatchers.eq("sku-456"),
                        org.mockito.ArgumentMatchers.eq(2));

        assertThrows(IllegalStateException.class,
                () -> orderService.placeOrder(new OrderRequest("product-123", 2, null), "request-123"));

        verify(orderRepository).save(any(Order.class));
        assertEquals(List.of(OrderStatus.PENDING), savedStatuses);
        verifyNoInteractions(orderPersistenceService);
    }

    @Test
    void doesNotReserveOrSaveWhenProductDoesNotExist() {
        when(productClient.getProductById("missing-product")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> orderService.placeOrder(new OrderRequest("missing-product", 2, null), "request-123"));

        verifyNoInteractions(inventoryClient, orderPersistenceService);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void returnsPreviouslyCreatedOrderForSameKeyAndRequest() {
        Order existingOrder = Order.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .idempotencyKey("request-123")
                .orderNumber("order-number")
                .productId("product-123")
                .quantity(2)
                .price(new BigDecimal("99.98"))
                .status(OrderStatus.CONFIRMED)
                .build();
        when(orderRepository.findByIdempotencyKey("request-123")).thenReturn(Optional.of(existingOrder));

        OrderResponse response = orderService.placeOrder(
                new OrderRequest("product-123", 2, null), "request-123");

        assertEquals("00000000-0000-0000-0000-000000000001", response.id());
        assertEquals("order-number", response.orderNumber());
        assertEquals(OrderStatus.CONFIRMED, response.status());
        verifyNoInteractions(productClient, inventoryClient, orderPersistenceService);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void retriesInventoryReservationForPreviouslyPendingOrder() {
        Order pendingOrder = Order.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .idempotencyKey("request-123")
                .orderNumber("original-order-number")
                .productId("product-123")
                .quantity(2)
                .price(new BigDecimal("99.98"))
                .status(OrderStatus.PENDING)
                .build();
        when(orderRepository.findByIdempotencyKey("request-123")).thenReturn(Optional.of(pendingOrder));
        when(productClient.getProductById("product-123")).thenReturn(Optional.of(
                new ProductResponse("product-123", "sku-456", "Keyboard", "Mechanical keyboard",
                        new BigDecimal("49.99"))));
        when(orderPersistenceService.confirmAndEnqueue(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setStatus(OrderStatus.CONFIRMED);
            return order;
        });

        OrderResponse response = orderService.placeOrder(
                new OrderRequest("product-123", 2, null), "request-123");

        verify(inventoryClient).reduceStock("original-order-number", "sku-456", 2);
        verify(orderPersistenceService).confirmAndEnqueue(any(Order.class));
        assertEquals(OrderStatus.CONFIRMED, response.status());
        assertEquals(OrderStatus.CONFIRMED, pendingOrder.getStatus());
    }

    @Test
    void rejectsReusingKeyForDifferentRequest() {
        Order existingOrder = Order.builder()
                .idempotencyKey("request-123")
                .productId("product-123")
                .quantity(2)
                .status(OrderStatus.CONFIRMED)
                .build();
        when(orderRepository.findByIdempotencyKey("request-123")).thenReturn(Optional.of(existingOrder));

        assertThrows(IdempotencyKeyConflictException.class,
                () -> orderService.placeOrder(new OrderRequest("product-123", 3, null), "request-123"));

        verifyNoInteractions(productClient, inventoryClient, orderPersistenceService);
    }

    @Test
    void returnsConcurrentlyCreatedOrderWhenUniqueIndexRejectsDuplicateKey() {
        Order existingOrder = Order.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .idempotencyKey("request-123")
                .orderNumber("order-number")
                .productId("product-123")
                .quantity(2)
                .price(new BigDecimal("99.98"))
                .status(OrderStatus.CONFIRMED)
                .build();
        when(productClient.getProductById("product-123")).thenReturn(Optional.of(
                new ProductResponse("product-123", "sku-456", "Keyboard", "Mechanical keyboard",
                        new BigDecimal("49.99"))));
        when(orderRepository.save(any(Order.class))).thenThrow(new DuplicateKeyException("duplicate"));
        AtomicInteger lookupCount = new AtomicInteger();
        when(orderRepository.findByIdempotencyKey("request-123")).thenAnswer(invocation ->
                lookupCount.getAndIncrement() == 0 ? Optional.empty() : Optional.of(existingOrder));

        OrderResponse response = orderService.placeOrder(
                new OrderRequest("product-123", 2, null), "request-123");

        assertEquals("00000000-0000-0000-0000-000000000001", response.id());
        assertEquals("order-number", response.orderNumber());
        verifyNoInteractions(inventoryClient, orderPersistenceService);
    }
}
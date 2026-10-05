package com.rahul.ms.order.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import com.rahul.ms.order.client.InventoryClient;
import com.rahul.ms.order.client.ProductClient;
import com.rahul.ms.order.dto.OrderRequest;
import com.rahul.ms.order.dto.OrderResponse;
import com.rahul.ms.order.entity.Order;
import com.rahul.ms.order.entity.OrderEventOutbox;
import com.rahul.ms.order.entity.OrderStatus;
import com.rahul.ms.order.exception.IdempotencyKeyConflictException;
import com.rahul.ms.order.repository.OrderEventOutboxRepository;
import com.rahul.ms.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final OrderEventOutboxRepository orderEventOutboxRepository;

    public OrderResponse placeOrder(OrderRequest orderRequest, String idempotencyKey) {
        var existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOrder.isPresent()) {
            return replay(existingOrder.get(), orderRequest);
        }

        // 1. Fetch product details
        var productResponse = productClient.getProductById(orderRequest.productId())
                .orElseThrow(() -> new RuntimeException("Product not found with ID: " + orderRequest.productId()));

        var order = Order.builder()
                .idempotencyKey(idempotencyKey)
                .orderNumber(UUID.randomUUID().toString())
                .productId(orderRequest.productId())
                .quantity(orderRequest.quantity())
                .price(productResponse.price().multiply(BigDecimal.valueOf(orderRequest.quantity())))
                .status(OrderStatus.PENDING)
                .build();

        Order savedOrder;
        try {
            savedOrder = orderRepository.save(order);
        } catch (DuplicateKeyException exception) {
            var concurrentlyCreatedOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (concurrentlyCreatedOrder.isEmpty()) {
                throw exception;
            }
            return replay(concurrentlyCreatedOrder.get(), orderRequest);
        }

        return reserveAndConfirm(savedOrder, productResponse.skuCode());
    }

    private OrderResponse reserveAndConfirm(Order order, String skuCode) {
        try {
            inventoryClient.reduceStock(order.getOrderNumber(), skuCode, order.getQuantity());
        } catch (HttpClientErrorException.BadRequest exception) {
            order.setStatus(OrderStatus.REJECTED);
            orderRepository.save(order);
            throw new IllegalArgumentException("Inventory reservation was rejected", exception);
        }

        order.setStatus(OrderStatus.CONFIRMED);
        order = orderRepository.save(order);

        orderEventOutboxRepository.save(OrderEventOutbox.builder()
                .orderNumber(order.getOrderNumber())
                .productId(order.getProductId())
                .quantity(order.getQuantity())
                .price(order.getPrice())
                .nextAttemptAt(Instant.now())
                .build());

        return toResponse(order);
    }

    private OrderResponse replay(Order order, OrderRequest request) {
        if (!order.getProductId().equals(request.productId()) || order.getQuantity() != request.quantity()) {
            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key was already used for a different order request");
        }
        if (order.getStatus() == OrderStatus.REJECTED) {
            throw new IllegalArgumentException("Inventory reservation was rejected");
        }
        if (order.getStatus() == OrderStatus.PENDING) {
            var product = productClient.getProductById(order.getProductId())
                    .orElseThrow(() -> new RuntimeException(
                            "Product not found with ID: " + order.getProductId()));
            return reserveAndConfirm(order, product.skuCode());
        }
        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getProductId(),
                order.getQuantity(),
                order.getPrice(),
                order.getStatus()
        );
    }

    public List<OrderResponse> getAllOrders() {
        var orders = orderRepository.findAll();
        return orders.stream().map(order -> new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getProductId(),
                order.getQuantity(),
                order.getPrice(),
                order.getStatus()
        )).toList();
    }
}
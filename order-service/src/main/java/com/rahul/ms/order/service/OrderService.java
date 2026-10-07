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
        log.info("Received order request for productId={} quantity={}",
                orderRequest.productId(), orderRequest.quantity());
        var existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOrder.isPresent()) {
            log.info("Replaying order orderNumber={} status={}",
                    existingOrder.get().getOrderNumber(), existingOrder.get().getStatus());
            return replay(existingOrder.get(), orderRequest);
        }

        var productResponse = productClient.getProductById(orderRequest.productId())
                .orElseThrow(() -> {
                    log.warn("Unable to place order: product not found productId={}", orderRequest.productId());
                    return new RuntimeException("Product not found with ID: " + orderRequest.productId());
                });

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
                log.error("Order persistence reported a duplicate key but no matching order was found");
                throw exception;
            }
            log.info("Concurrent order request resolved by replaying orderNumber={}",
                    concurrentlyCreatedOrder.get().getOrderNumber());
            return replay(concurrentlyCreatedOrder.get(), orderRequest);
        }

        log.info("Created pending order orderNumber={} productId={} quantity={}",
                savedOrder.getOrderNumber(), savedOrder.getProductId(), savedOrder.getQuantity());
        return reserveAndConfirm(savedOrder, productResponse.skuCode());
    }

    private OrderResponse reserveAndConfirm(Order order, String skuCode) {
        try {
            inventoryClient.reduceStock(order.getOrderNumber(), skuCode, order.getQuantity());
        } catch (HttpClientErrorException.BadRequest exception) {
            log.warn("Inventory rejected reservation for orderNumber={} skuCode={} quantity={} status={}",
                    order.getOrderNumber(), skuCode, order.getQuantity(), exception.getStatusCode());
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

        log.info("Order confirmed orderNumber={} productId={} quantity={}",
                order.getOrderNumber(), order.getProductId(), order.getQuantity());
        return toResponse(order);
    }

    private OrderResponse replay(Order order, OrderRequest request) {
        if (!order.getProductId().equals(request.productId()) || order.getQuantity() != request.quantity()) {
            log.warn("Idempotency conflict for orderNumber={} requestedProductId={} requestedQuantity={}",
                    order.getOrderNumber(), request.productId(), request.quantity());
            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key was already used for a different order request");
        }
        if (order.getStatus() == OrderStatus.REJECTED) {
            log.warn("Cannot replay rejected order orderNumber={}", order.getOrderNumber());
            throw new IllegalArgumentException("Inventory reservation was rejected");
        }
        if (order.getStatus() == OrderStatus.PENDING) {
            var product = productClient.getProductById(order.getProductId())
                    .orElseThrow(() -> {
                        log.warn("Unable to resume pending order: product not found orderNumber={} productId={}",
                                order.getOrderNumber(), order.getProductId());
                        return new RuntimeException("Product not found with ID: " + order.getProductId());
                    });
            log.info("Resuming pending order orderNumber={}", order.getOrderNumber());
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
        log.debug("Retrieved {} orders", orders.size());
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
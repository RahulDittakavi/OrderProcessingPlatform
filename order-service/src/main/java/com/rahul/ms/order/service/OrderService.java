package com.rahul.ms.order.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rahul.ms.order.client.InventoryClient;
import com.rahul.ms.order.client.ProductClient;
import com.rahul.ms.order.dto.OrderRequest;
import com.rahul.ms.order.dto.OrderResponse;
import com.rahul.ms.order.entity.Order;
import com.rahul.ms.order.entity.OrderEventOutbox;
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

    @Transactional
    public OrderResponse placeOrder(OrderRequest orderRequest) {
        // 1. Fetch product details
        var productResponse = productClient.getProductById(orderRequest.productId())
                .orElseThrow(() -> new RuntimeException("Product not found with ID: " + orderRequest.productId()));

        // Inventory performs an atomic conditional reservation.
        inventoryClient.reduceStock(orderRequest.productId(), orderRequest.quantity());

        var order = Order.builder()
                .orderNumber(UUID.randomUUID().toString())
                .productId(orderRequest.productId())
                .quantity(orderRequest.quantity())
                .price(productResponse.price().multiply(BigDecimal.valueOf(orderRequest.quantity())))
                .build();

        var savedOrder = orderRepository.save(order);

        orderEventOutboxRepository.save(OrderEventOutbox.builder()
                .orderNumber(savedOrder.getOrderNumber())
                .productId(savedOrder.getProductId())
                .quantity(savedOrder.getQuantity())
                .price(savedOrder.getPrice())
                .nextAttemptAt(java.time.Instant.now())
                .build());

        return new OrderResponse(
                savedOrder.getId(),
                savedOrder.getOrderNumber(),
                savedOrder.getProductId(),
                savedOrder.getQuantity(),
                savedOrder.getPrice()
        );
    }

    public List<OrderResponse> getAllOrders() {
        var orders = orderRepository.findAll();
        return orders.stream().map(order -> new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getProductId(),
                order.getQuantity(),
                order.getPrice()
        )).toList();
    }
}
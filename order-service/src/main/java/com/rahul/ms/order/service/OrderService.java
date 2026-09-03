package com.rahul.ms.order.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.rahul.ms.order.repository.OrderRepository;
import com.rahul.ms.order.client.ProductClient;
import com.rahul.ms.order.dto.OrderRequest;
import com.rahul.ms.order.dto.OrderResponse;

import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class OrderService {
    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public OrderResponse placeOrder(OrderRequest orderRequest) {
        // Fetch product details from Product Service
        var productResponse = productClient.getProductById(orderRequest.productId());

        // Create and save the order
        
        var order = com.rahul.ms.order.entity.Order.builder()
                .orderNumber(UUID.randomUUID().toString())
                .productId(orderRequest.productId())
                .quantity(orderRequest.quantity())
                .price(productResponse.price().multiply(BigDecimal.valueOf(orderRequest.quantity())))
                .build();

        var savedOrder = orderRepository.save(order);
        return new OrderResponse(
        savedOrder.getId(),
        savedOrder.getOrderNumber(),
        savedOrder.getProductId(),
        savedOrder.getQuantity(),
        savedOrder.getPrice()
        );
    }


    



}

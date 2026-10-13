package com.rahul.ms.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rahul.ms.order.entity.Order;
import com.rahul.ms.order.entity.OrderEventOutbox;
import com.rahul.ms.order.entity.OrderStatus;
import com.rahul.ms.order.entity.OutboxEventStatus;
import com.rahul.ms.order.event.OrderPlacedEvent;
import com.rahul.ms.order.repository.OrderEventOutboxRepository;
import com.rahul.ms.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderPersistenceService {

    private final OrderRepository orderRepository;
    private final OrderEventOutboxRepository outboxRepository;

    @Transactional
    public Order confirmAndEnqueue(Order order) {
        order.setStatus(OrderStatus.CONFIRMED);
        Order savedOrder = orderRepository.save(order);
        outboxRepository.save(OrderEventOutbox.builder()
                .aggregateId(savedOrder.getOrderNumber())
                .eventType("OrderPlaced")
                .payload(OrderPlacedEvent.builder()
                        .orderNumber(savedOrder.getOrderNumber())
                        .productId(savedOrder.getProductId())
                        .quantity(savedOrder.getQuantity())
                        .price(savedOrder.getPrice())
                        .build())
                .status(OutboxEventStatus.PENDING)
                .retryCount(0)
                .build());
        return savedOrder;
    }
}

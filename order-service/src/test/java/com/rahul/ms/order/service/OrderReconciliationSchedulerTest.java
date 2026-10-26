package com.rahul.ms.order.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rahul.ms.order.entity.Order;
import com.rahul.ms.order.entity.OrderStatus;
import com.rahul.ms.order.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderReconciliationSchedulerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderReconciliationScheduler scheduler;

    @Test
    void resumesEachStalePendingOrder() {
        UUID orderId = UUID.randomUUID();
        Order staleOrder = Order.builder()
                .id(orderId)
                .orderNumber("order-123")
                .status(OrderStatus.PENDING)
                .build();
        when(orderRepository.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(OrderStatus.PENDING), any())).thenReturn(List.of(staleOrder));

        scheduler.reconcileStaleOrders();

        verify(orderService).reconcileStalePendingOrder(orderId);
    }
}

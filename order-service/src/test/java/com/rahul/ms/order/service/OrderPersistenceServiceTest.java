package com.rahul.ms.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import com.rahul.ms.order.entity.Order;
import com.rahul.ms.order.entity.OrderEventOutbox;
import com.rahul.ms.order.entity.OrderStatus;
import com.rahul.ms.order.entity.OutboxEventStatus;
import com.rahul.ms.order.repository.OrderEventOutboxRepository;
import com.rahul.ms.order.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderPersistenceServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventOutboxRepository outboxRepository;

    @InjectMocks
    private OrderPersistenceService persistenceService;

    @Test
    void confirmsOrderAndPersistsItsOutboxEventTransactionally() throws Exception {
        Order order = Order.builder()
                .orderNumber("order-123")
                .productId("product-456")
                .quantity(2)
                .price(new BigDecimal("20.00"))
                .status(OrderStatus.PENDING)
                .build();
        when(orderRepository.save(order)).thenReturn(order);
        when(outboxRepository.save(any(OrderEventOutbox.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order savedOrder = persistenceService.confirmAndEnqueue(order);

        assertEquals(OrderStatus.CONFIRMED, savedOrder.getStatus());
        ArgumentCaptor<OrderEventOutbox> outboxCaptor = ArgumentCaptor.forClass(OrderEventOutbox.class);
        verify(orderRepository).save(order);
        verify(outboxRepository).save(outboxCaptor.capture());
        OrderEventOutbox outbox = outboxCaptor.getValue();
        assertEquals("order-123", outbox.getAggregateId());
        assertEquals("OrderPlaced", outbox.getEventType());
        assertEquals(OutboxEventStatus.PENDING, outbox.getStatus());
        assertEquals("product-456", outbox.getPayload().getProductId());
        assertEquals(2, outbox.getPayload().getQuantity());
        assertNotNull(OrderPersistenceService.class
                .getMethod("confirmAndEnqueue", Order.class)
                .getAnnotation(Transactional.class));
    }
}

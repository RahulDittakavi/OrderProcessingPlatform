package com.rahul.ms.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.rahul.ms.order.entity.OrderEventOutbox;
import com.rahul.ms.order.entity.OutboxEventStatus;
import com.rahul.ms.order.event.OrderPlacedEvent;
import com.rahul.ms.order.repository.OrderEventOutboxRepository;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock
    private OrderEventOutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @InjectMocks
    private OrderEventPublisher publisher;

    @Test
    void marksOutboxEventSentAfterKafkaAcknowledges() {
        OrderEventOutbox outbox = pendingEvent();
        SendResult<String, OrderPlacedEvent> sendResult = mock();
        RecordMetadata metadata = mock();
        when(outboxRepository.lockNextPendingEvents()).thenReturn(List.of(outbox));
        when(kafkaTemplate.send(eq("order-placed-topic"), eq("order-123"), any(OrderPlacedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.topic()).thenReturn("order-placed-topic");
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(1L);

        publisher.publishPendingEvents();

        assertEquals(OutboxEventStatus.SENT, outbox.getStatus());
        assertNotNull(outbox.getSentAt());
        verify(outboxRepository).save(outbox);
    }

    @Test
    void incrementsRetryCountAndLeavesFailedEventPending() {
        OrderEventOutbox outbox = pendingEvent();
        when(outboxRepository.lockNextPendingEvents()).thenReturn(List.of(outbox));
        when(kafkaTemplate.send(eq("order-placed-topic"), eq("order-123"), any(OrderPlacedEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")));

        publisher.publishPendingEvents();

        assertEquals(OutboxEventStatus.PENDING, outbox.getStatus());
        assertEquals(1, outbox.getRetryCount());
        verify(outboxRepository).save(outbox);
    }

    private OrderEventOutbox pendingEvent() {
        return OrderEventOutbox.builder()
                .aggregateId("order-123")
                .eventType("OrderPlaced")
                .payload(OrderPlacedEvent.builder()
                        .orderNumber("order-123")
                        .productId("product-456")
                        .quantity(2)
                        .price(new BigDecimal("20.00"))
                        .build())
                .status(OutboxEventStatus.PENDING)
                .retryCount(0)
                .build();
    }
}

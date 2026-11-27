package com.rahul.ms.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    private OrderEventOutboxClaimService claimService;

    @Mock
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @InjectMocks
    private OrderEventPublisher publisher;

    @Test
    void marksOutboxEventSentAfterKafkaAcknowledges() {
        OrderEventOutbox outbox = claimedEvent();
        SendResult<String, OrderPlacedEvent> sendResult = mock();
        RecordMetadata metadata = mock();
        when(claimService.claimNextEvents()).thenReturn(List.of(outbox));
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
        OrderEventOutbox outbox = claimedEvent();
        when(claimService.claimNextEvents()).thenReturn(List.of(outbox));
        when(kafkaTemplate.send(eq("order-placed-topic"), eq("order-123"), any(OrderPlacedEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")));

        publisher.publishPendingEvents();

        assertEquals(OutboxEventStatus.PENDING, outbox.getStatus());
        assertEquals(1, outbox.getRetryCount());
        assertRetryScheduledWithin(outbox.getNextAttemptAt(), 5);
        verify(outboxRepository).save(outbox);
    }

    @Test
    void keepsEventRetryingUntilTheHigherMaxRetryCountIsReached() {
        OrderEventOutbox outbox = claimedEvent();
        outbox.setRetryCount(99);
        when(claimService.claimNextEvents()).thenReturn(List.of(outbox));
        when(kafkaTemplate.send(eq("order-placed-topic"), eq("order-123"), any(OrderPlacedEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")));

        publisher.publishPendingEvents();

        assertEquals(OutboxEventStatus.FAILED, outbox.getStatus());
        assertEquals(100, outbox.getRetryCount());
        assertNull(outbox.getNextAttemptAt());
        verify(outboxRepository).save(outbox);
    }

    @Test
    void continuesPublishingBatchAfterKafkaFailure() {
        OrderEventOutbox failedEvent = claimedEvent();
        OrderEventOutbox nextEvent = claimedEvent();
        when(claimService.claimNextEvents()).thenReturn(List.of(failedEvent, nextEvent));
        when(kafkaTemplate.send(eq("order-placed-topic"), eq("order-123"), any(OrderPlacedEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")));

        publisher.publishPendingEvents();

        verify(kafkaTemplate, times(2))
                .send(eq("order-placed-topic"), eq("order-123"), any(OrderPlacedEvent.class));
        assertEquals(1, failedEvent.getRetryCount());
        assertEquals(1, nextEvent.getRetryCount());
    }

    @Test
    void releasesUnprocessedClaimsWhenPublishingIsInterrupted() throws Exception {
        OrderEventOutbox interruptedEvent = claimedEvent();
        OrderEventOutbox unprocessedEvent = claimedEvent();
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, OrderPlacedEvent>> interruptedFuture = mock(CompletableFuture.class);
        when(claimService.claimNextEvents()).thenReturn(List.of(interruptedEvent, unprocessedEvent));
        when(kafkaTemplate.send(eq("order-placed-topic"), eq("order-123"), any(OrderPlacedEvent.class)))
                .thenReturn(interruptedFuture);
        when(interruptedFuture.get(5, TimeUnit.SECONDS)).thenThrow(new InterruptedException());

        try {
            publisher.publishPendingEvents();
        } finally {
            Thread.interrupted();
        }

        assertEquals(OutboxEventStatus.PENDING, unprocessedEvent.getStatus());
        assertNull(unprocessedEvent.getNextAttemptAt());
        verify(kafkaTemplate).send(eq("order-placed-topic"), eq("order-123"), any(OrderPlacedEvent.class));
        verify(outboxRepository).saveAll(List.of(unprocessedEvent));
    }

    @Test
    void doublesRetryDelayOnSubsequentFailures() {
        OrderEventOutbox outbox = claimedEvent();
        outbox.setRetryCount(1);
        when(claimService.claimNextEvents()).thenReturn(List.of(outbox));
        when(kafkaTemplate.send(eq("order-placed-topic"), eq("order-123"), any(OrderPlacedEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")));

        publisher.publishPendingEvents();

        assertEquals(2, outbox.getRetryCount());
        assertRetryScheduledWithin(outbox.getNextAttemptAt(), 10);
    }

    @Test
    void capsRetryDelayAtFiveMinutes() {
        OrderEventOutbox outbox = claimedEvent();
        outbox.setRetryCount(6);
        when(claimService.claimNextEvents()).thenReturn(List.of(outbox));
        when(kafkaTemplate.send(eq("order-placed-topic"), eq("order-123"), any(OrderPlacedEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")));

        publisher.publishPendingEvents();

        assertEquals(7, outbox.getRetryCount());
        assertRetryScheduledWithin(outbox.getNextAttemptAt(), 300);
    }

    @Test
    void incrementsRetryCountWhenKafkaAcknowledgementTimesOut() throws Exception {
        OrderEventOutbox outbox = claimedEvent();
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, OrderPlacedEvent>> timedOutFuture = mock(CompletableFuture.class);
        when(claimService.claimNextEvents()).thenReturn(List.of(outbox));
        when(kafkaTemplate.send(eq("order-placed-topic"), eq("order-123"), any(OrderPlacedEvent.class)))
                .thenReturn(timedOutFuture);
        when(timedOutFuture.get(5, TimeUnit.SECONDS)).thenThrow(new TimeoutException("ack timeout"));

        publisher.publishPendingEvents();

        assertEquals(OutboxEventStatus.PENDING, outbox.getStatus());
        assertEquals(1, outbox.getRetryCount());
        verify(timedOutFuture).get(5, TimeUnit.SECONDS);
        verify(outboxRepository).save(outbox);
    }

    private OrderEventOutbox claimedEvent() {
        return OrderEventOutbox.builder()
                .aggregateId("order-123")
                .eventType("OrderPlaced")
                .payload(OrderPlacedEvent.builder()
                        .orderNumber("order-123")
                        .productId("product-456")
                        .quantity(2)
                        .price(new BigDecimal("20.00"))
                        .build())
                .status(OutboxEventStatus.PROCESSING)
                .retryCount(0)
                .build();
    }

    private void assertRetryScheduledWithin(Instant nextAttemptAt, long expectedDelaySeconds) {
        assertNotNull(nextAttemptAt);
        Instant now = Instant.now();
        assertFalse(nextAttemptAt.isBefore(now.plusSeconds(expectedDelaySeconds - 1)));
        assertTrue(nextAttemptAt.isBefore(now.plusSeconds(expectedDelaySeconds + 1)));
    }
}

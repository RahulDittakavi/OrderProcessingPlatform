package com.rahul.ms.order.service;

import java.time.Instant;
import java.util.concurrent.ExecutionException;

import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.rahul.ms.order.entity.OrderEventOutbox;
import com.rahul.ms.order.entity.OutboxEventStatus;
import com.rahul.ms.order.event.OrderPlacedEvent;
import com.rahul.ms.order.repository.OrderEventOutboxRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final OrderEventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    @Scheduled(
            fixedDelayString = "${order.events.publisher-delay:5000}",
            initialDelayString = "${order.events.publisher.initial-delay:0}")
    @Transactional
    public void publishPendingEvents() {
        for (OrderEventOutbox outbox : outboxRepository.lockNextPendingEvents()) {
            if (!publish(outbox)) {
                break;
            }
        }
    }

    private boolean publish(OrderEventOutbox outbox) {
        try {
            var result = kafkaTemplate.send(
                    "order-placed-topic", outbox.getAggregateId(), outbox.getPayload()).get();
            outbox.setStatus(OutboxEventStatus.SENT);
            outbox.setSentAt(Instant.now());
            outboxRepository.save(outbox);
            var metadata = result.getRecordMetadata();
            log.info("Published order event aggregateId={} topic={} partition={} offset={}",
                    outbox.getAggregateId(), metadata.topic(), metadata.partition(), metadata.offset());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordFailure(outbox, exception);
            return false;
        } catch (ExecutionException | KafkaException exception) {
            recordFailure(outbox, exception);
            return true;
        }
    }

    private void recordFailure(OrderEventOutbox outbox, Exception exception) {
        outbox.setRetryCount(outbox.getRetryCount() + 1);
        outboxRepository.save(outbox);
        log.error("Failed to publish order event aggregateId={} retryCount={}",
                outbox.getAggregateId(), outbox.getRetryCount(), exception);
    }
}

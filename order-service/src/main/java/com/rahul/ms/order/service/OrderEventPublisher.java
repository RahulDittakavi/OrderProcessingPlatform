package com.rahul.ms.order.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.rahul.ms.order.entity.OrderEventOutbox;
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

    @Scheduled(fixedDelayString = "${order.events.publisher-delay:5000}")
    public void publishPendingEvents() {
        outboxRepository.findTop100ByPublishedFalseAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(Instant.now())
                .forEach(this::publish);
    }

    private void publish(OrderEventOutbox outbox) {
        OrderPlacedEvent event = OrderPlacedEvent.builder()
                .orderNumber(outbox.getOrderNumber())
                .productId(outbox.getProductId())
                .quantity(outbox.getQuantity())
                .price(outbox.getPrice())
                .build();

        kafkaTemplate.send("order-placed-topic", outbox.getOrderNumber(), event)
                .whenComplete((result, exception) -> {
                    if (exception == null) {
                        outbox.setPublished(true);
                        outboxRepository.save(outbox);
                        var metadata = result.getRecordMetadata();
                        log.info("Published order event orderNumber={} topic={} partition={} offset={}",
                                outbox.getOrderNumber(), metadata.topic(), metadata.partition(), metadata.offset());
                    } else {
                        outbox.setAttempts(outbox.getAttempts() + 1);
                        Duration retryDelay = backoff(outbox.getAttempts());
                        outbox.setNextAttemptAt(Instant.now().plus(retryDelay));
                        outboxRepository.save(outbox);
                        log.error("Failed to publish order event orderNumber={} attempt={} retryDelaySeconds={}",
                                outbox.getOrderNumber(), outbox.getAttempts(), retryDelay.toSeconds(), exception);
                    }
                });
    }

    private Duration backoff(int attempts) {
        return Duration.ofSeconds(Math.min(60, 1L << Math.min(attempts, 6)));
    }
}
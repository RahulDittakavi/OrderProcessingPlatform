package com.rahul.ms.order.service;

import org.springframework.stereotype.Component;

import com.rahul.ms.order.entity.OutboxEventStatus;
import com.rahul.ms.order.repository.OrderEventOutboxRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class OutboxMetrics {

    public OutboxMetrics(OrderEventOutboxRepository outboxRepository, MeterRegistry meterRegistry) {
        Gauge.builder("order_outbox_failed_events", outboxRepository,
                        repository -> repository.countByStatus(OutboxEventStatus.FAILED))
                .description("Number of order outbox events that have exhausted their retries")
                .register(meterRegistry);
    }
}

package com.rahul.ms.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rahul.ms.order.entity.OutboxEventStatus;
import com.rahul.ms.order.repository.OrderEventOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class OutboxMetricsTest {

    @Test
    void exposesCurrentFailedOutboxEventCountAsGauge() {
        var outboxRepository = mock(OrderEventOutboxRepository.class);
        var meterRegistry = new SimpleMeterRegistry();
        when(outboxRepository.countByStatus(OutboxEventStatus.FAILED)).thenReturn(3L);

        new OutboxMetrics(outboxRepository, meterRegistry);

        assertEquals(3.0, meterRegistry.get("order_outbox_failed_events").gauge().value());
    }
}

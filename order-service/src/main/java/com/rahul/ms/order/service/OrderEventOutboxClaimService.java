package com.rahul.ms.order.service;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rahul.ms.order.entity.OrderEventOutbox;
import com.rahul.ms.order.entity.OutboxEventStatus;
import com.rahul.ms.order.repository.OrderEventOutboxRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderEventOutboxClaimService {

    private final OrderEventOutboxRepository outboxRepository;

    @Value("${order.events.claim-lease-ms:600000}")
    private long claimLeaseMs = 1200000;

    @Transactional
    public List<OrderEventOutbox> claimNextEvents() {
        List<OrderEventOutbox> events = outboxRepository.lockNextAvailableEvents();
        Instant leaseExpiresAt = Instant.now().plusMillis(claimLeaseMs);
        for (OrderEventOutbox event : events) {
            event.setStatus(OutboxEventStatus.PROCESSING);
            event.setNextAttemptAt(leaseExpiresAt);
        }
        return outboxRepository.saveAll(events);
    }
}

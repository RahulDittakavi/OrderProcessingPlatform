package com.rahul.ms.order.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rahul.ms.order.entity.OrderEventOutbox;
import com.rahul.ms.order.entity.OutboxEventStatus;
import com.rahul.ms.order.repository.OrderEventOutboxRepository;

@ExtendWith(MockitoExtension.class)
class OrderEventOutboxClaimServiceTest {

    @Mock
    private OrderEventOutboxRepository outboxRepository;

    @InjectMocks
    private OrderEventOutboxClaimService claimService;

    @Test
    void marksClaimedEventsAsProcessingWithAnExpiry() {
        OrderEventOutbox event = OrderEventOutbox.builder()
                .status(OutboxEventStatus.PENDING)
                .build();
        when(outboxRepository.lockNextAvailableEvents()).thenReturn(List.of(event));
        when(outboxRepository.saveAll(List.of(event))).thenReturn(List.of(event));

        Instant beforeClaim = Instant.now();
        List<OrderEventOutbox> claimedEvents = claimService.claimNextEvents();
        Instant afterClaim = Instant.now();

        assertEquals(List.of(event), claimedEvents);
        assertEquals(OutboxEventStatus.PROCESSING, event.getStatus());
        assertNotNull(event.getNextAttemptAt());
        assertFalse(event.getNextAttemptAt().isBefore(beforeClaim.plusSeconds(1199)));
        assertTrue(event.getNextAttemptAt().isBefore(afterClaim.plusSeconds(1201)));
        verify(outboxRepository).saveAll(List.of(event));
    }
}

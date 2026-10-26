package com.rahul.ms.order.service;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.rahul.ms.order.entity.OrderStatus;
import com.rahul.ms.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderReconciliationScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Value("${order.reconciliation.stale-after-ms:300000}")
    private long staleAfterMs = 300000;

    @Scheduled(
            fixedDelayString = "${order.reconciliation.delay-ms:60000}",
            initialDelayString = "${order.reconciliation.initial-delay-ms:60000}")
    public void reconcileStaleOrders() {
        Instant cutoff = Instant.now().minusMillis(staleAfterMs);
        for (var order : orderRepository.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                OrderStatus.PENDING, cutoff)) {
            try {
                orderService.reconcileStalePendingOrder(order.getId());
            } catch (RuntimeException exception) {
                log.error("Failed to reconcile stale pending order orderNumber={}",
                        order.getOrderNumber(), exception);
            }
        }
    }
}

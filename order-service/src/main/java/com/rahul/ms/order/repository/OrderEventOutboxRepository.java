package com.rahul.ms.order.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.rahul.ms.order.entity.OrderEventOutbox;

public interface OrderEventOutboxRepository extends JpaRepository<OrderEventOutbox, UUID> {

    @Query(value = """
            SELECT *
            FROM outbox_events
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT 100
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OrderEventOutbox> lockNextPendingEvents();
}

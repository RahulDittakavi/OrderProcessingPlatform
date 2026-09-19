package com.rahul.ms.order.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.rahul.ms.order.entity.OrderEventOutbox;

public interface OrderEventOutboxRepository extends MongoRepository<OrderEventOutbox, String> {

    List<OrderEventOutbox> findTop100ByPublishedFalseAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            Instant now);
}
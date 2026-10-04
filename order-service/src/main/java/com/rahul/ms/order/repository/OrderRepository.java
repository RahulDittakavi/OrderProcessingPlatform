package com.rahul.ms.order.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.rahul.ms.order.entity.Order;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
}

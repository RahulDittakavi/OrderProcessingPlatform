package com.rahul.ms.order.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.rahul.ms.order.entity.Order;

public interface OrderRepository extends MongoRepository<Order, String> {

}

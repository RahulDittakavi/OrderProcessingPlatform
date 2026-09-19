package com.rahul.ms.order.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "order-event-outbox")
public class OrderEventOutbox {

    @Id
    private String id;
    private String orderNumber;
    private String productId;
    private int quantity;
    private BigDecimal price;
    private boolean published;
    private int attempts;
    private Instant nextAttemptAt;
}
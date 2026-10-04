package com.rahul.ms.order.entity;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Document(collection = "orders")
public class Order {
    @Id
    private String id;
    @Indexed(unique = true, sparse = true)
    private String idempotencyKey;
    private String orderNumber;
    private String productId;
    private int quantity;
    private BigDecimal price;
    private OrderStatus status;

}

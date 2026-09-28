package com.rahul.ms.order.dto;

import java.math.BigDecimal;

import com.rahul.ms.order.entity.OrderStatus;
import lombok.Builder;

@Builder 
public record OrderResponse(
    String id,
    String orderNumber,
    String productId,
    int quantity,
    BigDecimal price,
    OrderStatus status
) {

}

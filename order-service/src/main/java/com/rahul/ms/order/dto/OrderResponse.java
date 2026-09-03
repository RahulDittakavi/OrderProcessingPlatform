package com.rahul.ms.order.dto;

import java.math.BigDecimal;

import lombok.Builder;

@Builder 
public record OrderResponse(
    String id,
    String orderNumber,
    String productId,
    int quantity,
    BigDecimal price
) {

}

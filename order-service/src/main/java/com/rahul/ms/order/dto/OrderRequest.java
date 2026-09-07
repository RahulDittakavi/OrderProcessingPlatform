package com.rahul.ms.order.dto;

import java.math.BigDecimal;

public record OrderRequest(
        String productId,
        Integer quantity,
        BigDecimal price
) {
} 

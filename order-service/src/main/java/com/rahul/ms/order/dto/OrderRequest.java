package com.rahul.ms.order.dto;

public record OrderRequest(
    String productId,
    int quantity
) {
} 

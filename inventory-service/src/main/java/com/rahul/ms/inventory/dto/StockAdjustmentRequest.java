package com.rahul.ms.inventory.dto;

import jakarta.validation.constraints.Positive;

public record StockAdjustmentRequest(@Positive(message = "Quantity must be positive") int quantity) {
}

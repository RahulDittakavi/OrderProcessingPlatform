package com.rahul.ms.product.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProductRequest(
	@NotBlank(message = "Product name is required") String name,
	@NotBlank(message = "Product description is required") String description,
	@NotNull(message = "Product price is required")
	@Positive(message = "Product price must be positive") BigDecimal price) {
}
package com.rahul.ms.product.model;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @NotBlank(message = "Product SKU is required")
    @Column(name = "sku_code", nullable = false)
    private String skuCode;
    @Column(nullable = false, columnDefinition = "text")
    @NotBlank(message = "Product name is required")
    private String name;
    @Column(nullable = false, columnDefinition = "text")
    @NotBlank(message = "Product description is required")
    private String description;
    @Column(nullable = false, precision = 19, scale = 2)
    @NotNull(message = "Product price is required")
    @Positive(message = "Product price must be a positive value")
    private BigDecimal price;

}
package com.rahul.ms.inventory.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rahul.ms.inventory.dto.InventoryResponse;
import com.rahul.ms.inventory.repository.InventoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public List<InventoryResponse> isInStock(List<String> skuCodes) {
        log.info("Checking stock for skuCodes: {}", skuCodes);

        return inventoryRepository.findBySkuCodeIn(skuCodes).stream()
                .map(inventory -> InventoryResponse.builder()
                        .skuCode(inventory.getSkuCode())
                        .isInStock(inventory.getQuantity() > 0)
                        .availableQuantity(inventory.getQuantity())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryResponse checkStockBySku(String skuCode) {
        log.info("Checking stock for single skuCode: {}", skuCode);

        return inventoryRepository.findBySkuCode(skuCode)
                .map(inventory -> InventoryResponse.builder()
                        .skuCode(inventory.getSkuCode())
                        .isInStock(inventory.getQuantity() > 0)
                        .availableQuantity(inventory.getQuantity())
                        .build())
                .orElse(InventoryResponse.builder()
                        .skuCode(skuCode)
                        .isInStock(false)
                        .availableQuantity(0)
                        .build());
    }
   
    @Transactional
    public void reduceStock(String skuCode, int quantity) {
        if (skuCode == null || skuCode.isBlank()) {
            throw new IllegalArgumentException("SKU code is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        int updatedRows = inventoryRepository.reserveStock(skuCode, quantity);
        if (updatedRows == 0) {
            log.warn("Unable to reserve {} units for skuCode: {}", quantity, skuCode);
            throw new IllegalArgumentException("Insufficient stock or inventory not found for skuCode: " + skuCode);
        }

        log.info("Reserved {} units for skuCode: {}", quantity, skuCode);
    }
    }

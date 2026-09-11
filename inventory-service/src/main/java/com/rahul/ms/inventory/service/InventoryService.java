package com.rahul.ms.inventory.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rahul.ms.inventory.dto.InventoryResponse;
import com.rahul.ms.inventory.entity.Inventory;
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
    log.info("Reducing stock for skuCode: {} by quantity: {}", skuCode, quantity);

    Inventory inventory = inventoryRepository.findBySkuCode(skuCode)
            .orElseThrow(() -> {
                log.warn("Inventory not found for skuCode: {}", skuCode);
                return new IllegalArgumentException("Inventory not found for skuCode: " + skuCode);
            });

    if (inventory.getQuantity() < quantity) {
        log.warn("Insufficient stock for skuCode: {}. Available: {}, Requested: {}", 
                skuCode, inventory.getQuantity(), quantity);
        throw new IllegalArgumentException("Insufficient stock for skuCode: " + skuCode);
    }

    inventory.setQuantity(inventory.getQuantity() - quantity);
    inventoryRepository.save(inventory);
    log.info("Stock reduced successfully for skuCode: {}. Remaining: {}", skuCode, inventory.getQuantity());
}
    }

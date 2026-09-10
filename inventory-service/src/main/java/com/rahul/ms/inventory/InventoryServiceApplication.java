package com.rahul.ms.inventory;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;

import com.rahul.ms.inventory.entity.Inventory;
import com.rahul.ms.inventory.repository.InventoryRepository;

@SpringBootApplication
@EnableDiscoveryClient
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner loadData(InventoryRepository inventoryRepository) {
        return args -> {
            if (inventoryRepository.count() == 0) {
                inventoryRepository.save(Inventory.builder()
                        .skuCode("iphone_15")
                        .quantity(100)
                        .build());
                inventoryRepository.save(Inventory.builder()
                        .skuCode("pixel_8")
                        .quantity(0)
                        .build());
            }
        };
    }
}
package com.rahul.ms.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rahul.ms.inventory.entity.Inventory;
import com.rahul.ms.inventory.repository.InventoryRepository;
import com.rahul.ms.inventory.repository.InventoryReservationRepository;
import com.rahul.ms.inventory.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class InventoryServiceApplicationTests {

	@Autowired
	private InventoryRepository inventoryRepository;

	@Autowired
	private InventoryReservationRepository reservationRepository;

	@Autowired
	private InventoryService inventoryService;

	@Test
	void contextLoads() {
	}

	@Test
	void repeatedReservationWithSameIdConsumesStockOnlyOnce() {
		reservationRepository.deleteAll();
		inventoryRepository.deleteAll();
		inventoryRepository.save(Inventory.builder().skuCode("sku-idempotent").quantity(10).build());

		inventoryService.reduceStock("order-idempotent", "sku-idempotent", 3);
		inventoryService.reduceStock("order-idempotent", "sku-idempotent", 3);

		assertEquals(7, inventoryRepository.findBySkuCode("sku-idempotent").orElseThrow().getQuantity());
		assertEquals(1, reservationRepository.count());
	}

}

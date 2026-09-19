package com.rahul.ms.inventory.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rahul.ms.inventory.repository.InventoryRepository;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Test
    void reservesStockWithAnAtomicRepositoryUpdate() {
        when(inventoryRepository.reserveStock("sku-1", 3)).thenReturn(1);

        new InventoryService(inventoryRepository).reduceStock("sku-1", 3);

        verify(inventoryRepository).reserveStock("sku-1", 3);
    }

    @Test
    void rejectsNonPositiveQuantitiesBeforeCallingRepository() {
        InventoryService service = new InventoryService(inventoryRepository);

        assertThrows(IllegalArgumentException.class, () -> service.reduceStock("sku-1", 0));
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void rejectsReservationWhenNoRowsWereUpdated() {
        when(inventoryRepository.reserveStock("sku-1", 3)).thenReturn(0);

        assertThrows(IllegalArgumentException.class,
                () -> new InventoryService(inventoryRepository).reduceStock("sku-1", 3));
    }
}
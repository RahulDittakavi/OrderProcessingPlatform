package com.rahul.ms.inventory.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rahul.ms.inventory.repository.InventoryRepository;
import com.rahul.ms.inventory.entity.InventoryReservation;
import com.rahul.ms.inventory.repository.InventoryReservationRepository;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryReservationRepository reservationRepository;

    @Test
    void reservesStockWithAnAtomicRepositoryUpdate() {
        when(inventoryRepository.reserveStock("sku-1", 3)).thenReturn(1);

        new InventoryService(inventoryRepository, reservationRepository).reduceStock("order-1", "sku-1", 3);

        verify(inventoryRepository).reserveStock("sku-1", 3);
        verify(reservationRepository).save(argThat(reservation ->
            reservation.getReservationId().equals("order-1")
                && reservation.getSkuCode().equals("sku-1")
                && reservation.getQuantity() == 3));
    }

        @Test
        void repeatedReservationDoesNotDecrementStockAgain() {
        when(reservationRepository.findById("order-1")).thenReturn(Optional.of(InventoryReservation.builder()
            .reservationId("order-1")
            .skuCode("sku-1")
            .quantity(3)
            .build()));

        new InventoryService(inventoryRepository, reservationRepository).reduceStock("order-1", "sku-1", 3);

        verify(inventoryRepository, never()).reserveStock("sku-1", 3);
        verify(reservationRepository, never()).save(org.mockito.ArgumentMatchers.any());
        }

        @Test
        void rejectsReusingReservationIdForDifferentRequest() {
        when(reservationRepository.findById("order-1")).thenReturn(Optional.of(InventoryReservation.builder()
            .reservationId("order-1")
            .skuCode("sku-1")
            .quantity(3)
            .build()));

        assertThrows(IllegalArgumentException.class,
            () -> new InventoryService(inventoryRepository, reservationRepository)
                .reduceStock("order-1", "sku-1", 4));
        verifyNoInteractions(inventoryRepository);
        }

    @Test
    void rejectsNonPositiveQuantitiesBeforeCallingRepository() {
        InventoryService service = new InventoryService(inventoryRepository, reservationRepository);

        assertThrows(IllegalArgumentException.class, () -> service.reduceStock("order-1", "sku-1", 0));
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void rejectsReservationWhenNoRowsWereUpdated() {
        when(inventoryRepository.reserveStock("sku-1", 3)).thenReturn(0);

        assertThrows(IllegalArgumentException.class,
                () -> new InventoryService(inventoryRepository, reservationRepository)
                    .reduceStock("order-1", "sku-1", 3));
    }
}
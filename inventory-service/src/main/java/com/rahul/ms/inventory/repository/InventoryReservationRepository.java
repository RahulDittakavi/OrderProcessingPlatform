package com.rahul.ms.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rahul.ms.inventory.entity.InventoryReservation;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, String> {
}
package com.rahul.ms.inventory.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rahul.ms.inventory.entity.InventoryReservation;

import jakarta.persistence.LockModeType;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InventoryReservation r where r.reservationId = :reservationId")
    Optional<InventoryReservation> findByReservationIdForUpdate(
            @Param("reservationId") String reservationId);
}
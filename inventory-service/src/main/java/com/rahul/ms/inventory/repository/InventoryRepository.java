package com.rahul.ms.inventory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import com.rahul.ms.inventory.entity.Inventory;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findBySkuCode(String skuCode);

    List<Inventory> findBySkuCodeIn(List<String> skuCodes);

    @Modifying
    @Query("update Inventory i set i.quantity = i.quantity - :quantity "
            + "where i.skuCode = :skuCode and i.quantity >= :quantity")
    int reserveStock(@Param("skuCode") String skuCode, @Param("quantity") int quantity);
}
CREATE TABLE IF NOT EXISTS t_inventory_reservation (
    reservation_id VARCHAR(36) PRIMARY KEY,
    sku_code VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0)
);
ALTER TABLE orders
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX idx_orders_status_created_at
    ON orders (status, created_at);

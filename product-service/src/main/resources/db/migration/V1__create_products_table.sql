CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku_code TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    price NUMERIC(19, 2) NOT NULL
);

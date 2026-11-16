CREATE TABLE order_item (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id),
    product_id UUID NOT NULL REFERENCES product (id),
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12, 2),
    unit_cost NUMERIC(12, 2),
    product_name VARCHAR(120),

    CONSTRAINT uq_order_item_order_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_item_unit_price CHECK (unit_price IS NULL OR unit_price > 0),
    CONSTRAINT ck_order_item_unit_cost CHECK (unit_cost IS NULL OR unit_cost >= 0),
    CONSTRAINT ck_order_item_snapshot CHECK (
        (unit_price IS NULL) = (unit_cost IS NULL) AND (unit_price IS NULL) = (product_name IS NULL)
    )
);

CREATE INDEX idx_order_item_product ON order_item (product_id);
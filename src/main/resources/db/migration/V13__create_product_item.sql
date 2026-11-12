CREATE TABLE product_item (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES product (id),
    ingredient_id UUID NOT NULL REFERENCES ingredient (id),
    quantity NUMERIC(14, 4) NOT NULL,
    CONSTRAINT uq_product_item_product_ingredient UNIQUE (product_id, ingredient_id),
    CONSTRAINT ck_product_item_quantity CHECK (quantity > 0)
);

CREATE INDEX ix_product_item_ingredient_id ON product_item (ingredient_id);
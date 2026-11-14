CREATE TABLE product (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    recipe_id UUID NOT NULL REFERENCES recipe (id),
    recipe_quantity NUMERIC(14,4) NOT NULL,
    retail_price NUMERIC(12,2),
    wholesale_price NUMERIC(12,2),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_product_recipe_quantity_positive CHECK (recipe_quantity > 0),
    CONSTRAINT ck_product_retail_price_positive CHECK (retail_price IS NULL OR retail_price > 0),
    CONSTRAINT ck_product_wholesale_price_positive CHECK (wholesale_price IS NULL OR wholesale_price > 0)
);

CREATE INDEX ix_product_recipe_id ON product (recipe_id);
CREATE UNIQUE INDEX ux_product_name ON product (lower(name));

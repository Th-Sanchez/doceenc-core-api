CREATE TABLE ingredient (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    brand_supplier VARCHAR(120),
    category_id UUID NOT NULL REFERENCES category (id),
    base_unit VARCHAR(20) NOT NULL,
    package_price NUMERIC(12, 2) NOT NULL,
    package_quantity NUMERIC(14, 4) NOT NULL,
    unit_price NUMERIC(14, 6) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_ingredient_base_unit CHECK (base_unit IN ('GRAM', 'MILLILITER', 'UNIT')),
    CONSTRAINT ck_ingredient_package_price CHECK (package_price > 0),
    CONSTRAINT ck_ingredient_package_quantity CHECK (package_quantity > 0)
);

CREATE INDEX ix_ingredient_category_id ON ingredient (category_id);
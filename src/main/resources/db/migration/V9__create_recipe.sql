CREATE TABLE recipe (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    category_id UUID NOT NULL REFERENCES category (id),
    yield_quantity NUMERIC(14, 4) NOT NULL,
    yield_unit VARCHAR(20) NOT NULL,
    prep_time_minutes INTEGER,
    shelf_life_days INTEGER,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_recipe_yield_unit CHECK (yield_unit IN ('GRAM', 'MILLILITER', 'UNIT')),
    CONSTRAINT ck_recipe_yield_quantity CHECK (yield_quantity > 0),
    CONSTRAINT ck_recipe_prep_time CHECK (prep_time_minutes IS NULL OR prep_time_minutes > 0),
    CONSTRAINT ck_recipe_shelf_life CHECK (shelf_life_days IS NULL OR shelf_life_days > 0)
);

CREATE INDEX ix_recipe_category_id ON recipe (category_id);
CREATE UNIQUE INDEX ux_recipe_name ON recipe (lower(name));
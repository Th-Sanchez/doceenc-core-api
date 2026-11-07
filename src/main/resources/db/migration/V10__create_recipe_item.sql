CREATE TABLE recipe_item (
    id UUID PRIMARY KEY,
    recipe_id UUID NOT NULL REFERENCES recipe (id),
    ingredient_id UUID NOT NULL REFERENCES ingredient (id),
    quantity NUMERIC(14, 4) NOT NULL,
    CONSTRAINT uq_recipe_item_recipe_ingredient UNIQUE (recipe_id, ingredient_id),
    CONSTRAINT ck_recipe_item_quantity CHECK (quantity > 0)
);

CREATE INDEX ix_recipe_item_ingredient_id ON recipe_item (ingredient_id);
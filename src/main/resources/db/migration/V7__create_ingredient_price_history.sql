CREATE TABLE ingredient_price_history (
    id UUID PRIMARY KEY,
    ingredient_id UUID NOT NULL REFERENCES ingredient (id),
    package_price NUMERIC(12, 2) NOT NULL,
    package_quantity NUMERIC(14, 4) NOT NULL,
    unit_price NUMERIC(14, 6) NOT NULL,
    purchased_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_price_history_ingredient_purchased
    ON ingredient_price_history (ingredient_id, purchased_on DESC, id DESC);
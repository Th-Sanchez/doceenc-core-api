DROP INDEX IF EXISTS ix_price_history_ingredient_purchased;

CREATE INDEX ix_price_history_ingredient_purchased
    ON ingredient_price_history (ingredient_id, purchased_on DESC, created_at DESC);

CREATE UNIQUE INDEX ux_ingredient_name_brand
    ON ingredient (lower(name), lower(coalesce(brand_supplier, '')));
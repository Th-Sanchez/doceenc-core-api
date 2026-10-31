CREATE TABLE category (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    type VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_category_type CHECK (type IN ('INGREDIENT', 'RECIPE'))
);

CREATE UNIQUE INDEX ux_category_type_name ON category (type, lower(name));
CREATE TABLE customer (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(20) NOT NULL,
    phone VARCHAR(20),
    address VARCHAR(255),
    store_name VARCHAR(120),
    notes VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_customer_type CHECK (type IN ('RETAIL', 'WHOLESALE')),
    CONSTRAINT ck_customer_store_name CHECK (
        (type = 'RETAIL' AND store_name IS NULL)
        OR
        (type = 'WHOLESALE' AND store_name IS NOT NULL AND btrim(store_name) <> '')
    )
);
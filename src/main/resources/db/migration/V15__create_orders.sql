CREATE SEQUENCE order_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    order_number BIGINT NOT NULL DEFAULT nextval('order_number_seq') UNIQUE,
    customer_id UUID NOT NULL REFERENCES customer (id),
    status VARCHAR(20) NOT NULL,
    fulfillment_type VARCHAR(20) NOT NULL,
    price_channel VARCHAR(20),
    delivery_address VARCHAR(255),
    delivery_date DATE,
    notes VARCHAR(1000),
    total_amount NUMERIC(14, 2),
    confirmed_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_orders_fulfillment_type CHECK (fulfillment_type IN ('PICKUP', 'DELIVERY')),
    CONSTRAINT ck_orders_price_channel CHECK (price_channel IS NULL OR price_channel IN ('RETAIL', 'WHOLESALE')),
    CONSTRAINT ck_orders_delivery_address CHECK ((fulfillment_type = 'DELIVERY') = (delivery_address IS NOT NULL)),
    CONSTRAINT ck_orders_total_amount CHECK (total_amount IS NULL OR total_amount >= 0),
    CONSTRAINT ck_orders_snapshot CHECK (
        (status = 'PENDING' AND total_amount IS NULL AND price_channel IS NULL AND confirmed_at IS NULL)
        OR status = 'CANCELLED'
        OR (status IN ('CONFIRMED', 'DELIVERED')
            AND total_amount IS NOT NULL AND price_channel IS NOT NULL AND confirmed_at IS NOT NULL)
    ),
    CONSTRAINT ck_orders_delivered_at CHECK ((status = 'DELIVERED') = (delivered_at IS NOT NULL)),
    CONSTRAINT ck_orders_cancelled_at CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);

ALTER SEQUENCE order_number_seq OWNED BY orders.order_number;

CREATE INDEX idx_orders_delivery_date ON orders (delivery_date);
CREATE INDEX idx_orders_status_delivery_date ON orders (status, delivery_date);
CREATE INDEX idx_orders_customer ON orders (customer_id);
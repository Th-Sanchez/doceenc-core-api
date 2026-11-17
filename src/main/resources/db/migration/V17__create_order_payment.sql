CREATE TABLE order_payment (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    type VARCHAR(20) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    method VARCHAR(20) NOT NULL,
    paid_on DATE NOT NULL,
    voided_at TIMESTAMPTZ,
    notes VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_order_payment_type CHECK (type IN ('PAYMENT', 'REFUND')),
    CONSTRAINT ck_order_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_order_payment_method CHECK (method IN ('PIX', 'CASH', 'CARD')),
    CONSTRAINT ck_order_payment_void CHECK (voided_at IS NULL OR type = 'PAYMENT')
);

CREATE INDEX idx_order_payment_order ON order_payment (order_id);

CREATE INDEX idx_order_payment_paid_on ON order_payment (paid_on) WHERE voided_at IS NULL;
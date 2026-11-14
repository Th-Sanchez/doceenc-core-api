CREATE TABLE settings (
    id SMALLINT PRIMARY KEY,
    target_margin_retail NUMERIC(5, 2) NOT NULL,
    target_margin_wholesale NUMERIC(5, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_settings_singleton CHECK (id = 1),
    CONSTRAINT ck_settings_retail_range CHECK (target_margin_retail >= 0 AND target_margin_retail < 100),
    CONSTRAINT ck_settings_wholesale_range CHECK (target_margin_wholesale >= 0 AND target_margin_wholesale < 100)
);

INSERT INTO settings (id, target_margin_retail, target_margin_wholesale, created_at, updated_at)
VALUES (1, 50.00, 35.00, now(), now());
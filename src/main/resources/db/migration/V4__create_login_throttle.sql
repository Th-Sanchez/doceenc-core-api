CREATE TABLE login_throttle (
    email VARCHAR(255) PRIMARY KEY,
    failed_count INT NOT NULL,
    last_failed_at TIMESTAMPTZ NOT NULL,
    locked_until TIMESTAMPTZ
);

CREATE INDEX ix_login_throttle_last_failed_at ON login_throttle (last_failed_at);
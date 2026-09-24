CREATE TABLE currency (
    code CHAR(3) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    minor_units SMALLINT NOT NULL CHECK (minor_units BETWEEN 0 AND 8),
    CONSTRAINT ck_currency_code CHECK (code ~ '^[A-Z]{3}$')
);

INSERT INTO currency (code, name, minor_units) VALUES
    ('USD', 'US Dollar', 2),
    ('BRL', 'Brazilian Real', 2);

CREATE TABLE exchange_rate (
    id UUID PRIMARY KEY,
    base_currency CHAR(3) NOT NULL REFERENCES currency(code),
    quote_currency CHAR(3) NOT NULL REFERENCES currency(code),
    rate NUMERIC(18,8) NOT NULL CHECK (rate > 0),
    source VARCHAR(100) NOT NULL CHECK (btrim(source) <> ''),
    effective_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_exchange_rate_distinct_currencies CHECK (base_currency <> quote_currency)
);

CREATE INDEX idx_exchange_rate_latest
    ON exchange_rate (base_currency, quote_currency, effective_at DESC, created_at DESC, id DESC);

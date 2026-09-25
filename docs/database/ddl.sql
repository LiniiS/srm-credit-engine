-- Estado implementado até E1-S3.
-- Fonte: migrations Flyway V1__initialize_platform.sql, V2__create_exchange_rates.sql
-- e V3__create_base_rates.sql, nesta ordem.

CREATE TABLE application_metadata (
    metadata_key VARCHAR(100) PRIMARY KEY,
    metadata_value VARCHAR(255) NOT NULL
);

INSERT INTO application_metadata (metadata_key, metadata_value)
VALUES ('schema_version', '1');

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

CREATE TABLE base_rate (
    id UUID PRIMARY KEY,
    currency_code CHAR(3) NOT NULL REFERENCES currency(code),
    rate_monthly NUMERIC(18,12) NOT NULL CHECK (rate_monthly >= 0),
    effective_from DATE NOT NULL,
    source VARCHAR(64) NOT NULL CHECK (btrim(source) <> ''),
    CONSTRAINT uk_base_rate_currency_effective_from UNIQUE (currency_code, effective_from)
);

CREATE INDEX idx_base_rate_applicable
    ON base_rate (currency_code, effective_from DESC);

INSERT INTO base_rate (id, currency_code, rate_monthly, effective_from, source) VALUES
    ('11111111-1111-4111-8111-111111111111', 'BRL', 0.010000000000, DATE '2026-01-01', 'DEMO_SEED'),
    ('22222222-2222-4222-8222-222222222222', 'USD', 0.005000000000, DATE '2026-01-01', 'DEMO_SEED');

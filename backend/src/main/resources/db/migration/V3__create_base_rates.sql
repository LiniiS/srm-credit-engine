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

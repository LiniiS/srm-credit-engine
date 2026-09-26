CREATE TABLE receivable_type (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    strategy_key VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uk_receivable_type_code UNIQUE (code),
    CONSTRAINT ck_receivable_type_code_not_blank CHECK (btrim(code) <> ''),
    CONSTRAINT ck_receivable_type_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_receivable_type_strategy_key_not_blank CHECK (btrim(strategy_key) <> ''),
    CONSTRAINT ck_receivable_type_version_non_negative CHECK (version >= 0)
);

INSERT INTO receivable_type (id, code, name, strategy_key, active, version) VALUES
    ('33333333-3333-4333-8333-333333333333', 'DUPLICATA_MERCANTIL', 'Duplicata Mercantil', 'DUPLICATA_MERCANTIL', TRUE, 0),
    ('44444444-4444-4444-8444-444444444444', 'CHEQUE_PRE_DATADO', 'Cheque Pré-datado', 'CHEQUE_PRE_DATADO', TRUE, 0);

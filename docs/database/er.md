# Diagrama entidade-relacionamento

## Estado implementado até E1-S1

A fundação mantém metadados técnicos e agora inclui o catálogo USD/BRL e o histórico append-only de taxas.

```mermaid
erDiagram
  APPLICATION_METADATA {
    varchar metadata_key PK
    varchar metadata_value
  }
  CURRENCY ||--o{ EXCHANGE_RATE : "base"
  CURRENCY ||--o{ EXCHANGE_RATE : "quote"
  CURRENCY {
    char code PK
    varchar name
    smallint minor_units
  }
  EXCHANGE_RATE {
    uuid id PK
    char base_currency FK
    char quote_currency FK
    numeric rate
    varchar source
    timestamptz effective_at
    timestamptz created_at
  }
```

O DDL vigente está em `docs/database/ddl.sql` e deriva da migration Flyway
`V1__initialize_platform.sql` e `V2__create_exchange_rates.sql`.

## Modelo de negócio aprovado para stories posteriores

```mermaid
erDiagram
  CURRENCY ||--o{ EXCHANGE_RATE : "base"
  CURRENCY ||--o{ EXCHANGE_RATE : "quote"
  CURRENCY ||--o{ RECEIVABLE : "denomina"
  CURRENCY ||--o{ BASE_RATE : "taxa mensal"
  CURRENCY ||--o{ SETTLEMENT_BATCH : "pagamento"
  ASSIGNOR ||--o{ RECEIVABLE : "possui"
  ASSIGNOR ||--o{ SETTLEMENT_BATCH : "cede"
  RECEIVABLE_TYPE ||--o{ RECEIVABLE : "classifica"
  BASE_RATE ||--o{ SETTLEMENT_ITEM : "referenciada"
  SETTLEMENT_BATCH ||--|{ SETTLEMENT_ITEM : "contém"
  RECEIVABLE ||--o| SETTLEMENT_ITEM : "liquidado em"
  EXCHANGE_RATE |o--o{ SETTLEMENT_ITEM : "aplicada"

  CURRENCY {
    char code PK
    varchar name
    smallint minor_units
  }
  EXCHANGE_RATE {
    uuid id PK
    char base_currency FK
    char quote_currency FK
    numeric rate
    varchar source
    timestamptz effective_at
    timestamptz created_at
  }
  RECEIVABLE_TYPE {
    uuid id PK
    varchar code UK
    varchar name
    numeric spread_monthly
    varchar strategy_key
    boolean active
    bigint version
  }
  BASE_RATE {
    uuid id PK
    char currency_code FK
    numeric rate_monthly
    date effective_from
  }
  ASSIGNOR {
    uuid id PK
    char document UK
    varchar legal_name
  }
  RECEIVABLE {
    uuid id PK
    uuid assignor_id FK
    varchar external_id UK
    uuid receivable_type_id FK
    numeric face_value
    char face_currency FK
    date due_date
    varchar status
    bigint version
  }
  SETTLEMENT_BATCH {
    uuid id PK
    uuid assignor_id FK
    char payment_currency FK
    varchar idempotency_key UK
    char request_hash
    varchar status
    numeric total_net
    timestamptz created_at
    timestamptz settled_at
  }
  SETTLEMENT_ITEM {
    uuid id PK
    uuid batch_id FK
    uuid receivable_id FK,UK
    uuid base_rate_id FK
    date original_due_date
    date adjusted_due_date
    int term_days
    numeric base_rate_applied
    numeric spread_applied
    numeric present_value
    numeric discount
    uuid fx_rate_id FK
    numeric fx_rate_applied
    numeric net_value
  }
```

Este trecho representa o planejamento aprovado, não o schema já implementado.
Ele será reconciliado incrementalmente com cada migration Flyway das stories
correspondentes.

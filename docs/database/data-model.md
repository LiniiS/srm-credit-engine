# Modelo de dados — estado implementado e evolução planejada

O modelo privilegia auditabilidade, integridade e consultas por período. Flyway é a
fonte normativa do schema já implementado; tabelas de stories posteriores permanecem
identificadas como planejamento.

| Tabela | Finalidade | Restrições principais |
|---|---|---|
| `currency` | Catálogo ISO das moedas | `code CHAR(3)` PK; `minor_units >= 0`. |
| `exchange_rate` | Histórico append-only de câmbio | par distinto; `rate > 0`; índice por par+vigência. |
| `receivable_type` | Catálogo interno e chave estável da Strategy | Implementado na V4: `code` único; nome/chave obrigatórios; estado ativo e versionamento não negativo; sem spread ou nome de classe Java. |
| `base_rate` | Histórico versionado da taxa base mensal por moeda | Implementado na V3: `(currency_code, effective_from)` único; `NUMERIC(18,12)` não negativo; origem obrigatória até 64 caracteres. |
| `assignor` | Cedente | documento único; nome obrigatório. |
| `receivable` | Identidade e estado do ativo | `(assignor_id, external_id)` único; `version` para concorrência. |
| `settlement_batch` | Cabeçalho da liquidação | `idempotency_key` única; payload hash; status e totais. |
| `settlement_item` | Item e snapshots do cálculo | `receivable_id` único; FKs e checks financeiros. |

## Colunas financeiras planejadas

- Valores monetários: `NUMERIC(19,2)` no MVP BRL/USD.
- Taxas base e snapshots futuros: `NUMERIC(18,12)`; os spreads da E2-S1 ficam nas Strategies como `BigDecimal`, não no catálogo.
- Câmbio: `NUMERIC(18,8)`.
- Instantes: `TIMESTAMPTZ`; datas de negócio: `DATE`.
- IDs: UUID/UUIDv7 gerado pela aplicação ou banco — decisão de detalhe antes da primeira migration.

## Taxas base implementadas

`rate_monthly` é uma fração decimal: `0.010000000000` representa 1% ao mês. A
versão aplicável possui o maior `effective_from` menor ou igual à data de cálculo.
Os seeds BRL e USD vigentes desde `2026-01-01` usam `source=DEMO_SEED` e UUIDs
reservados; são dados fictícios reproduzíveis, não taxas oficiais ou de mercado.
`BaseRateQuery` é a única porta pública desta capacidade e devolve id, moeda, valor,
vigência e origem. Não existe escrita produtiva pública.

## Tipos de recebível implementados

A V4 cadastra `DUPLICATA_MERCANTIL` e `CHEQUE_PRE_DATADO` com UUIDs determinísticos,
`active=true`, `version=0` e `strategy_key` igual ao código. O banco não persiste
spread nem nome de classe Java. O registry resolve a chave para
`DuplicataMercantilPricingStrategy` (`0.015` a.m.) ou
`ChequePreDatadoPricingStrategy` (`0.025` a.m.); os valores são frações decimais
construídas com `BigDecimal`. Não existe endpoint ou escrita produtiva do catálogo.

## Snapshots em `settlement_item`

`original_due_date`, `adjusted_due_date`, `term_days`, `base_rate_applied`, `spread_applied`, `present_value`, `discount`, `fx_rate_id`, `fx_rate_applied` e `net_value`. O prazo usa ACT/30 a partir do vencimento ajustado pelo calendário brasileiro configurável; a reconstrução não depende da configuração corrente.

## Integridade concorrente

- Constraint única em `settlement_item.receivable_id` impede dois batches vencedores.
- `receivable.version` e transição de estado oferecem detecção antecipada e erro claro.
- `settlement_batch.idempotency_key` mais `request_hash` diferencia replay de colisão semântica.
- Transação única persiste batch, itens e estado dos recebíveis.

## Índices iniciais

- `exchange_rate(base_currency, quote_currency, effective_at DESC)`.
- `base_rate(currency_code, effective_from DESC)` único.
- `receivable(assignor_id, external_id)` único.
- `settlement_batch(settled_at DESC)`.
- `settlement_batch(assignor_id, settled_at DESC)`.
- `settlement_batch(payment_currency, settled_at DESC)`.
- Índices adicionais só após `EXPLAIN ANALYZE`, evitando otimização especulativa.

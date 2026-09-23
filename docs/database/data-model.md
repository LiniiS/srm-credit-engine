# Modelo de dados inicial — proposta

O modelo privilegia auditabilidade, integridade e consultas por período. Flyway será a fonte normativa quando a implementação começar; até lá, este documento e o ER são apenas proposta.

| Tabela | Finalidade | Restrições principais |
|---|---|---|
| `currency` | Catálogo ISO das moedas | `code CHAR(3)` PK; `minor_units >= 0`. |
| `exchange_rate` | Histórico append-only de câmbio | par distinto; `rate > 0`; índice por par+vigência. |
| `receivable_type` | Tipo, spread e chave da Strategy | `code` único; spread não negativo; versionamento. |
| `base_rate` | Histórico da taxa base mensal | vigência única; taxa não negativa. |
| `assignor` | Cedente | documento único; nome obrigatório. |
| `receivable` | Identidade e estado do ativo | `(assignor_id, external_id)` único; `version` para concorrência. |
| `settlement_batch` | Cabeçalho da liquidação | `idempotency_key` única; payload hash; status e totais. |
| `settlement_item` | Item e snapshots do cálculo | `receivable_id` único; FKs e checks financeiros. |

## Colunas financeiras propostas

- Valores monetários: `NUMERIC(19,2)` no MVP BRL/USD.
- Taxas/spreads: `NUMERIC(18,12)` para reduzir perda intermediária persistida.
- Câmbio: `NUMERIC(18,8)`.
- Instantes: `TIMESTAMPTZ`; datas de negócio: `DATE`.
- IDs: UUID/UUIDv7 gerado pela aplicação ou banco — decisão de detalhe antes da primeira migration.

## Snapshots em `settlement_item`

`term_days`, `base_rate_applied`, `spread_applied`, `present_value`, `discount`, `fx_rate_id`, `fx_rate_applied` e `net_value`. A reconstrução não depende da configuração corrente.

## Integridade concorrente

- Constraint única em `settlement_item.receivable_id` impede dois batches vencedores.
- `receivable.version` e transição de estado oferecem detecção antecipada e erro claro.
- `settlement_batch.idempotency_key` mais `request_hash` diferencia replay de colisão semântica.
- Transação única persiste batch, itens e estado dos recebíveis.

## Índices iniciais

- `exchange_rate(base_currency, quote_currency, effective_at DESC)`.
- `receivable(assignor_id, external_id)` único.
- `settlement_batch(settled_at DESC)`.
- `settlement_batch(assignor_id, settled_at DESC)`.
- `settlement_batch(payment_currency, settled_at DESC)`.
- Índices adicionais só após `EXPLAIN ANALYZE`, evitando otimização especulativa.


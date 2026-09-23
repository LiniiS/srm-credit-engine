# Baseline arquitetural — SRM Credit Engine

Arquitetura de referência calibrada para o nível **Sênior** do desafio (com os itens de Especialista tratados como documentação). É o ponto de partida; qualquer desvio precisa de ADR.

## Sumário
1. Visão geral e containers
2. Módulos e limites
3. Camadas e regra de dependência
4. Padrões de projeto adotados (e por quê)
5. Contrato de API
6. Modelo de dados
7. Convenções financeiras
8. Concorrência e integridade
9. Resiliência e observabilidade
10. Estrutura do repositório

---

## 1. Visão geral e containers (C4 nível 2)

| Container | Tecnologia | Responsabilidade |
|---|---|---|
| Web SPA | React + TypeScript + Vite | Painel do operador, simulação, grid de transações |
| API | Java 21 + Spring Boot 3 | Precificação, câmbio, liquidação, relatórios |
| Banco | PostgreSQL 16 | Fonte da verdade transacional, migrações Flyway |
| Provedor de câmbio (mock) | Adapter HTTP/in-memory | Simula integração externa de taxas |
| Observabilidade | Prometheus + Grafana (compose) | Métricas; logs JSON no stdout |

Atores (C4 nível 1): Operador da mesa, Provedor externo de câmbio, Auditoria/Compliance (consome extrato).

## 2. Módulos e limites (monólito modular)

Pacote raiz: `com.srm.creditengine`

| Módulo | Responsabilidade | Expõe | Depende de |
|---|---|---|---|
| `currency` | Moedas, taxas de câmbio, integração mock | `ExchangeRateQuery` (porta de leitura), endpoints de taxa | `shared` |
| `pricing` | Tipos de recebível, estratégias de risco, cálculo de VP | `PricingService`, `PricingStrategy` | `currency` (apenas `ExchangeRateQuery`), `shared` |
| `settlement` | Lote, liquidação, idempotência, locking | Endpoints de liquidação | `pricing`, `currency`, `shared` |
| `reporting` | Extrato de liquidação (leitura otimizada) | Endpoint de extrato | `shared` (lê tabelas via SQL; **não** chama `settlement`) |
| `shared` | `Money`, erros, clock, config transversal | Tipos base | nada |

Regras:
- Sem ciclos entre módulos. `pricing` não conhece `settlement`.
- Módulos só usam classes públicas de `api`/`domain` de outro módulo que estejam em pacote marcado como público (ex.: `..currency.domain.port..`). Internos (`persistence`) nunca são importados de fora.
- `reporting` é o único que atravessa em **2 camadas** (controller → query), como o desafio permite.

## 3. Camadas e regra de dependência

Por módulo:
```
<modulo>/
  api/            # Camada de aplicação: controllers, DTOs (records), mappers, validação de entrada
  domain/         # Camada de negócio: entidades/VOs, regras, strategies, serviços de domínio, ports (interfaces)
  service/        # Casos de uso: orquestração + fronteira transacional (@Transactional)
  persistence/    # Camada de persistência: JPA entities/repos, adapters que implementam ports, queries jOOQ
```
- As três camadas exigidas pelo desafio: **aplicação** (`api`), **negócio** (`service` + `domain`), **persistência** (`persistence`).
- `domain` não importa Spring Web, JPA nem Jackson (mantém regras testáveis sem contexto Spring). Se optar por anotações JPA no domínio por simplicidade, registre no ADR e mantenha-as fora das strategies.
- `api` → `service` → `domain` ← `persistence` (persistência implementa ports do domínio: DIP).
- Verificação: ArchUnit `layeredArchitecture()` + `noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "jakarta.persistence..")`.

## 4. Padrões adotados

| Padrão | Onde | Problema resolvido |
|---|---|---|
| Strategy | `PricingStrategy` por tipo de recebível | Nova regra de risco sem alterar o calculador (OCP) |
| Registry/Factory simples | `PricingStrategyRegistry` (Map injetado pelo Spring) | Resolver strategy por código do tipo sem `switch` |
| Value Object | `Money`, `Rate`, `Term`, `CurrencyCode` | Impedir mistura de moedas e escalas erradas |
| Ports & Adapters (leve) | `ExchangeRateProvider`, repositórios | Trocar mock por provedor real; testar domínio isolado |
| CQRS leve | Escrita JPA / leitura jOOQ | Relatórios performáticos sem distorcer o modelo de escrita |
| Idempotency Key | `POST /settlements` | Evitar liquidação duplicada em retry do cliente |
| Optimistic Locking | `@Version` em lote/liquidação | Conflito de liquidação concorrente → 409 |
| Circuit Breaker + Retry | Adapter do provedor de câmbio | Falha externa não derruba a precificação |

Rejeitados por padrão (YAGNI, documentar em ADR se questionado): Event Sourcing, Saga, microserviços, CQRS com bancos separados, cache distribuído, mensageria no código.

## 5. Contrato de API (v1)

Base: `/api/v1`. Erros em `application/problem+json` (RFC 9457).

| Método | Rota | Sucesso | Erros relevantes |
|---|---|---|---|
| POST | `/pricing/simulations` | 200 (não persiste) | 400, 422 |
| POST | `/settlements` (header `Idempotency-Key`) | 201 + `Location` | 400, 409 (conflito/lock), 422 (regra), 503 (câmbio indisponível) |
| GET | `/settlements/{id}` | 200 | 404 |
| GET | `/reports/settlements?from&to&assignorId&currency&page&size&sort` | 200 paginado | 400 |
| GET | `/exchange-rates/latest?base=USD&quote=BRL` | 200 | 404 |
| POST | `/exchange-rates` | 201 | 400, 422 |
| POST | `/exchange-rates/sync` (dispara mock) | 202 | 503 |
| GET | `/receivable-types` | 200 | — |

Convenções: valores monetários e taxas trafegam como **string decimal** no JSON (`"1234.56"`), nunca number; datas ISO-8601; `size` máx. 100; resposta paginada `{content, page, size, totalElements, totalPages}`.

## 6. Modelo de dados (PostgreSQL)

- `currency(code CHAR(3) PK, name, minor_units SMALLINT)`
- `exchange_rate(id, base_currency FK, quote_currency FK, rate NUMERIC(18,8), source, effective_at TIMESTAMPTZ, created_at)` — índice `(base_currency, quote_currency, effective_at DESC)`; append-only.
- `receivable_type(id, code UNIQUE, name, spread_monthly NUMERIC(9,6), strategy_key, active, version)`
- `base_rate(id, rate_monthly NUMERIC(9,6), effective_from DATE)` — se a taxa base for configurável.
- `assignor(id, document CHAR(14) UNIQUE, legal_name)` — cedente.
- `settlement_batch(id, assignor_id FK, payment_currency FK, status, idempotency_key UNIQUE, total_net NUMERIC(19,2), version BIGINT, created_at, settled_at)`
- `settlement_item(id, batch_id FK, receivable_type_id FK, face_value NUMERIC(19,2), face_currency FK, due_date, term_days, base_rate_applied, spread_applied, present_value NUMERIC(19,2), discount NUMERIC(19,2), fx_rate_id FK NULL, fx_rate_applied NUMERIC(18,8) NULL, net_value NUMERIC(19,2))`
- Snapshot de taxas aplicadas **copiado** no item: auditoria não pode depender de dados que mudam.
- Checks: `face_value > 0`, `net_value >= 0`, `status IN (...)`.
- Índices para o extrato: `settlement_batch(assignor_id, settled_at DESC)`, `settlement_batch(payment_currency, settled_at DESC)`, `settlement_batch(settled_at DESC)`.

## 7. Convenções financeiras (todas exigem ADR)

- `BigDecimal` em todo o fluxo; cálculo intermediário com `MathContext.DECIMAL128`; arredondamento **uma única vez** no valor final (escala 2, `HALF_EVEN` — justificar no ADR).
- Fórmula: `VP = VF / (1 + taxaBase + spread)^n`, com `n = prazoDias / 30` (recomendado). Expoente fracionário não é suportado por `BigDecimal.pow(int)`: usar `ch.obermuhlner:big-math` (`BigDecimalMath.pow`) ou `exp(n·ln(1+i))` em `BigDecimal`. **Nunca** `Math.pow` com `double`.
- Deságio = VF − VP (na moeda do título).
- Cross-currency: calcular VP na moeda do título, converter no fim com a taxa vigente e **gravar a taxa usada**. Convenção explícita do par: `rate` = quantas unidades de `quote` valem 1 `base` (USD/BRL = 5.10 ⇒ 1 USD = 5.10 BRL). BRL→USD: `valorUSD = valorBRL / rate(USD,BRL)`.
- Taxa de câmbio com idade acima de um limite configurável → recusar liquidação (422/503) em vez de liquidar com preço velho.

## 8. Concorrência e integridade

- Liquidação dentro de uma transação (`@Transactional` no caso de uso), isolamento padrão `READ COMMITTED` + `@Version`.
- `Idempotency-Key` com constraint única: segunda requisição com o mesmo payload reproduz a resposta existente; payload diferente com a mesma chave → `409 Conflict`.
- `ObjectOptimisticLockingFailureException` → 409 via handler global.
- Teste obrigatório: N threads liquidando o mesmo lote → exatamente 1 sucesso (Testcontainers + `ExecutorService`/`CountDownLatch`).

## 9. Resiliência e observabilidade

- Resilience4j no adapter do provedor de câmbio: timeout, retry com backoff exponencial (só para erros transitórios), circuit breaker; fallback = última taxa persistida se dentro do limite de idade.
- Logs JSON (logstash-logback-encoder) com `traceId`/`spanId`; documento do cedente mascarado.
- Micrometer + `/actuator/prometheus`: `srm_settlements_total{currency,type,outcome}`, `srm_pricing_duration`, métricas do circuit breaker.
- Tracing: Micrometer Tracing (OTel bridge) — exportador opcional.

## 10. Estrutura do repositório

```
/backend        Spring Boot (Maven ou Gradle — decidir em ADR)
/frontend       React + Vite
/docs           adr/, architecture/ (C4), database/ (ER, DDL), scale/, eda/
/infra          docker-compose, prometheus, grafana
/.github        workflows CI, PR template
AI_USAGE.md  README.md
```

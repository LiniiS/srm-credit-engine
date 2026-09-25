---
title: 'E1-S3 — Disponibilizar taxa base por moeda e vigência'
type: 'feature'
created: '2026-09-25'
status: 'in-review'
baseline_commit: '5c04a9ed4c1e1812b8a85358d2aa1110d878ce9e'
route: 'full'
route_source: 'auto'
review: 'thorough'
review_source: 'auto'
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']
review_loop_iteration: 0
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/docs/adr/0001-adotar-monolito-modular-hexagonal.md'
  - '{project-root}/docs/adr/0002-adotar-postgresql-flyway-jpa-jooq.md'
  - '{project-root}/docs/adr/0003-padronizar-calculo-financeiro-decimal.md'
  - '{project-root}/docs/adr/0004-padronizar-cambio-e-vigencia.md'
---

# Story E1-S3 — Disponibilizar taxa base por moeda e vigência

- **Épico:** E1 — Câmbio auditável
- **Status:** Review
- **Prioridade:** Must, como pré-condição da precificação
- **Predecessoras:** E0-S1, E0-S2, E1-S1 e E1-S2 concluídas

<frozen-after-approval reason="objetivo, critérios, modelo e limites pertencem à autora; mudanças exigem nova aprovação">

## Objetivo e valor

Disponibilizar no backend a taxa base mensal vigente para a moeda do título e uma data de cálculo, mantendo versões históricas no PostgreSQL e uma carga local fictícia, explícita e reproduzível para BRL e USD. O incremento prepara um insumo auditável para a futura precificação sem calcular preço, criar operação ou persistir snapshot de liquidação.

## Requisitos e rastreabilidade

| Fonte | Cobertura |
|---|---|
| E1 / E1-S3; regra 8 do PRD | Taxa base mensal versionada por moeda e vigência, com seeds fictícios identificados. |
| RF-04 | Pré-condição para a futura simulação de valor presente; nenhuma fórmula é implementada aqui. |
| RF-06/RF-08 | Prepara identidade e valor que poderão compor snapshots futuros, sem criar liquidação. |
| RNF-01 | `BigDecimal` e `NUMERIC(18,12)`; nenhum `double`/`float`/`Number`. |
| RNF-10 | Domínio unitário e schema/consulta em PostgreSQL 16 via Testcontainers. |
| RNF-11 | `currency` preserva `api → service → domain ← persistence` e os guardrails ArchUnit. |
| RNF-12 | A consulta fornece id, moeda, valor e vigência necessários ao snapshot futuro; a persistência do snapshot fica fora desta story. |
| ADR-0001 | Implementação no módulo `currency`; consumo futuro por `pricing` somente por porta pública. |
| ADR-0002 | Flyway é fonte do schema, JPA atende a leitura e PostgreSQL real valida migration. |
| ADR-0003 | Taxa mensal por moeda/vigência, precisão decimal e seeds explicitamente fictícios. |
| ADR-0004 | Taxa base permanece conceito distinto de taxa de câmbio, sem par BASE/QUOTE ou validade cambial. |

## Modelo de dados

Migration V3 cria `base_rate` sem modificar V1/V2:

| Coluna | Tipo/regra |
|---|---|
| `id` | `UUID PRIMARY KEY`; valor determinístico para cada seed. |
| `currency_code` | `CHAR(3) NOT NULL`, FK para `currency(code)`. |
| `rate_monthly` | `NUMERIC(18,12) NOT NULL`, `CHECK (rate_monthly >= 0)`. |
| `effective_from` | `DATE NOT NULL`; início inclusivo da vigência. |
| `source` | `VARCHAR(64) NOT NULL`; obrigatório, não vazio e com no máximo 64 caracteres. |

Há unicidade em `(currency_code, effective_from)` e índice de consulta em `(currency_code, effective_from DESC)`. A migration inclui exatamente os seeds abaixo; `rate_monthly` é uma fração decimal, portanto `0.010000000000` representa 1% ao mês. Os UUIDs reservados e reconhecíveis e a origem `DEMO_SEED` identificam dados demonstrativos, que não representam taxas oficiais ou de mercado. A migration não cria taxa de câmbio, spread ou massa de operação.

| Moeda | `rate_monthly` | `effective_from` | `source` | `id` |
|---|---:|---|---|---|
| BRL | `0.010000000000` | `2026-01-01` | `DEMO_SEED` | `11111111-1111-4111-8111-111111111111` |
| USD | `0.005000000000` | `2026-01-01` | `DEMO_SEED` | `22222222-2222-4222-8222-222222222222` |

## Política de versionamento e vigência

- Cada linha é uma versão imutável e append-only; não há update/delete produtivo.
- Para `currencyCode` e `calculationDate`, a versão vigente é a de maior `effective_from` que satisfaça `effective_from <= calculationDate`.
- A unicidade por moeda/data elimina empate de vigência; nova taxa para a mesma data é rejeitada, não sobrescrita.
- Versão futura pode existir, mas não é retornada antes de `effectiveFrom`.
- Ausência de moeda no catálogo gera `CURRENCY_NOT_SUPPORTED`; moeda catalogada sem versão vigente gera `BASE_RATE_NOT_FOUND`.
- A porta retorna ao consumidor futuro `id`, `currencyCode`, `rateMonthly`, `effectiveFrom` e `source`, permitindo que outra story grave id/valor/origem no snapshot sem reler configuração corrente.

## Contrato de consumo

Não há contrato REST na E1-S3: nenhum artefato de planejamento define endpoint público de taxa base. A única porta pública do módulo para esta capacidade é `BaseRateQuery`, consumível futuramente pelo módulo `pricing`, com operação equivalente a `findApplicable(CurrencyCode, LocalDate calculationDate)`. Não existe porta, caso de uso ou endpoint produtivo público de escrita. O domínio usa `BigDecimal` exato; `MathContext.DECIMAL128` será aplicado quando houver cálculo financeiro, não para arredondar ou alterar o valor consultado.

## Critérios de aceite

### AC1 — Schema versionado e íntegro

**Dado** um PostgreSQL 16 vazio
**Quando** Flyway aplicar V1–V3
**Então** `base_rate` existe com FK para `currency`, `NUMERIC(18,12)`, `DATE`, `source VARCHAR(64) NOT NULL`, checks de taxa não negativa e origem não vazia, unicidade e índice na ordem especificada
**E** V1/V2 permanecem inalteradas.

### AC2 — Seeds fictícios reproduzíveis

**Dado** o ambiente local recém-migrado
**Quando** os dados de referência forem consultados
**Então** existem exatamente BRL `0.010000000000`/`11111111-1111-4111-8111-111111111111` e USD `0.005000000000`/`22222222-2222-4222-8222-222222222222`, ambos vigentes desde `2026-01-01` e com `source=DEMO_SEED`
**E** as taxas são frações decimais mensais, os UUIDs são reservados e reconhecíveis e os registros estão explicitamente marcados como demonstração, não como taxas oficiais, de mercado, cambiais ou spreads.

### AC3 — Consulta seleciona a vigência correta

**Dadas** versões passadas e futuras para uma moeda catalogada
**Quando** a porta for consultada com uma data de cálculo
**Então** retorna a versão com maior `effectiveFrom` menor ou igual à data
**E** a fronteira `effectiveFrom == calculationDate` é inclusiva
**E** uma versão futura não é retornada antecipadamente.

### AC4 — Histórico permanece append-only

**Dada** uma versão existente
**Quando** testes prepararem uma nova vigência por fixture, SQL de teste ou colaborador de persistência package-private
**Então** uma nova linha é criada e a anterior permanece intacta
**E** não há porta, endpoint nem caso de uso produtivo público de escrita, edição ou exclusão
**E** moeda+vigência duplicada é rejeitada pelo banco sem sobrescrever histórico.

### AC5 — Erros de domínio são determinísticos

**Dado** código malformado, moeda não catalogada, data nula ou moeda catalogada sem taxa aplicável
**Quando** a consulta for executada
**Então** a entrada inválida é rejeitada antes da persistência, moeda ausente retorna `CURRENCY_NOT_SUPPORTED` e ausência de versão retorna `BASE_RATE_NOT_FOUND`
**E** nenhuma exceção de JPA/SQL vaza pela porta.

### AC6 — Precisão e arquitetura são preservadas

**Dado** valor mensal de escala até 12, inclusive zero, e origem obrigatória com até 64 caracteres
**Quando** for carregado e consultado
**Então** valor e origem retornam por igualdade, sem conversão binária ou arredondamento silencioso
**E** domínio/porta não dependem de Spring, JPA ou Jackson
**E** ArchUnit e a regressão de E1-S1/E1-S2 permanecem verdes.

## Matriz de comportamento e bordas

| Cenário | Entrada/estado | Resultado |
|---|---|---|
| Seed BRL | banco vazio migrado | `0.010000000000`, `2026-01-01`, `DEMO_SEED`, UUID `11111111-1111-4111-8111-111111111111`. |
| Seed USD | banco vazio migrado | `0.005000000000`, `2026-01-01`, `DEMO_SEED`, UUID `22222222-2222-4222-8222-222222222222`. |
| Fronteira inclusiva | data igual a `effective_from` | versão dessa data. |
| Entre vigências | duas versões passadas | maior `effective_from`. |
| Somente futura | moeda catalogada | `BASE_RATE_NOT_FOUND`. |
| Moeda não catalogada | `EUR` bem formatado | `CURRENCY_NOT_SUPPORTED`. |
| Código/data inválidos | formato inválido ou nulo | erro de validação de domínio. |
| Valor zero | `0.000000000000` | aceito, conforme modelo aprovado. |
| Valor negativo/escala > 12 | versão inválida | rejeição sem arredondamento/escrita. |
| Origem inválida | nula, vazia ou acima de 64 caracteres | rejeição sem escrita. |
| Mesma moeda e vigência | chave já existente | constraint rejeita; versão anterior intacta. |
| Consulta rastreável | versão vigente existente | retorna id, moeda, valor, vigência e origem. |

## Limites explícitos de escopo

**Incluído:** migration V3; seeds BRL/USD fictícios aprovados; domínio, única porta pública `BaseRateQuery`, serviço de consulta e adapter JPA no módulo `currency`; seleção por `LocalDate`; preparação de histórico exclusivamente em teste por fixture, SQL ou colaborador package-private; testes unitários, ArchUnit e Testcontainers; DDL/ER/modelo e documentação operacional afetada.

**Excluído:** endpoint REST/OpenAPI, porta pública de escrita, caso de uso produtivo de cadastro/edição/exclusão de taxa base; UI; scheduler/provider oficial; taxa de câmbio ou alterações no WireMock; spread/Strategy; fórmula, DECIMAL128 aplicado a cálculo, calendário, recebíveis, simulação, pricing, conversão, settlement, snapshot persistido e reporting. Não adicionar H2, Supabase, Kafka, Redis ou microserviço.

## Decisões humanas aprovadas

1. `base_rate.source` é `VARCHAR(64) NOT NULL`, obrigatório, não vazio e limitado a 64 caracteres; `BaseRateQuery` retorna também essa origem.
2. Os seeds são exatamente os registros BRL/USD descritos no modelo, com frações decimais mensais, vigência `2026-01-01`, origem `DEMO_SEED` e UUIDs reservados/reconhecíveis.
3. Os seeds são dados fictícios de demonstração e não representam taxas oficiais ou de mercado.
4. `BaseRateQuery` é a única porta pública; escrita existe somente como detalhe package-private de persistência ou preparação de teste, sem superfície produtiva pública.

</frozen-after-approval>

## Tarefas técnicas ordenadas

- [x] **T1 — Testes de domínio (AC2, AC3, AC5, AC6):** especificar por testes fração mensal, valor, origem, data inclusiva, erros e precisão sem framework.
- [x] **T2 — Migration V3 (AC1, AC2, AC4):** criar tabela, constraints de valor/origem, índice e os dois seeds exatos aprovados; preservar V1/V2.
- [x] **T3 — Domínio e porta (AC3, AC5, AC6):** criar `BaseRate`, erros e somente `BaseRateQuery` como porta pública no módulo `currency`, retornando id, moeda, valor, vigência e origem, sem anotações de framework.
- [x] **T4 — Persistência e serviço de consulta (AC3–AC6):** mapear entidade/adapter JPA e selecionar `effective_from <= calculationDate` em ordem descendente; distinguir catálogo ausente de versão ausente; manter qualquer colaborador de escrita package-private e sem caso de uso produtivo.
- [x] **T5 — Provas PostgreSQL/arquitetura (AC1–AC6):** validar schema por catálogo e inserções inválidas, seeds, append-only, bordas temporais, precisão e guardrails em Testcontainers.
- [x] **T6 — Documentação e fechamento:** derivar DDL/ER/modelo da V3; atualizar README/AI_USAGE/story apenas com fatos; executar gates e revisar o diff.

## Code Map e arquivos previstos

| Caminho | Ação prevista |
|---|---|
| `backend/src/main/resources/db/migration/V3__create_base_rates.sql` | Schema e seeds fictícios determinísticos. |
| `backend/src/main/java/com/srm/creditengine/currency/domain/**` | Agregado/VO e única porta pública `BaseRateQuery`. |
| `backend/src/main/java/com/srm/creditengine/currency/service/**` | Orquestração exclusiva da consulta e erros estáveis. |
| `backend/src/main/java/com/srm/creditengine/currency/persistence/**` | Entidade, repository, adapter de consulta e eventual colaborador de escrita package-private. |
| `backend/src/test/java/com/srm/creditengine/currency/**` | Unidade e integração PostgreSQL 16. |
| `backend/src/test/java/com/srm/creditengine/FlywayIntegrationTest.java` | Estrutura e seeds da V3, se a prova couber no teste existente. |
| `docs/database/ddl.sql`, `docs/database/er.md`, `docs/database/data-model.md` | Derivação e descrição fiéis da migration. |
| `README.md`, `AI_USAGE.md`, esta story | Uso local e evidências reais, somente durante implementação. |

Reutilizar `CurrencyCode`, catálogo `currency`, datasource/Flyway, `Clock` apenas onde realmente necessário, Testcontainers e regras ArchUnit existentes. Não alterar V1/V2, endpoints de câmbio, resiliência, Compose ou frontend.

## Estratégia de testes e gates

- **Domínio:** valor nulo, negativo, zero, escala 12/13; origem nula/vazia/com 64/65 caracteres; moeda/data nulas e pureza de framework.
- **Persistência:** V3 em banco vazio; FK, tipo/escala, `source VARCHAR(64)`, checks, unique e índice; seeds exatos; seleção anterior/igual/entre/futura; duplicidade e histórico preparados sem porta pública de escrita.
- **Serviço:** catálogo ausente versus versão ausente; resultado de `BaseRateQuery` preserva id, moeda, valor, vigência e origem exatos.
- **Regressão:** E1-S1/E1-S2, ArchUnit, JaCoCo, frontend e Compose continuam verdes; nenhum endpoint novo aparece no OpenAPI.

```bash
cd backend && ./mvnw -q spotless:check && ./mvnw -q verify
cd frontend && npm ci && npm run lint && npm run typecheck && npm run test -- --run && npm run build
docker compose config
docker compose up --build -d
docker compose ps
bash .agents/skills/srm-documentacao/scripts/check-docs.sh . story
git diff --check
docker compose down
```

## Riscos, dependências e pré-condições

- **Atendidas:** stories predecessoras `Done`; catálogo BRL/USD, PostgreSQL 16, Flyway/JPA, Testcontainers, CI e guardrails disponíveis.
- **Dados demonstrativos confundidos com oficiais:** mitigar com `source=DEMO_SEED`, UUIDs reservados e documentação explícita.
- **Confusão conceitual:** nomes e documentação devem impedir uso de `exchange_rate.rate` ou spreads como taxa base.
- **Vigência:** `DATE` é data de negócio, não `Instant`; timezone não pode deslocar a seleção.
- **Precisão:** JPA ou JSON não podem reduzir escala; não existe JSON nesta story.
- **Snapshot:** a porta prepara dados auditáveis, mas persistir snapshot agora anteciparia E2/E3.

## Definition of Ready

- [x] Objetivo, valor, predecessoras, rastreabilidade e limites definidos.
- [x] Modelo, política de vigência, contrato interno e ausência deliberada de REST definidos.
- [x] ACs, bordas, testes, gates e riscos especificados.
- [x] Baseline E1-S1/E1-S2 e ADRs aceitos considerados.
- [x] Valores, data, origem, UUIDs e semântica fracionária dos seeds aprovados humanamente.
- [x] Superfície pública restrita a `BaseRateQuery`, sem escrita produtiva pública.
- [x] Aprovação humana do Draft e promoção para `ready-for-dev`.

**Resultado da DoR:** integralmente atendida; decisões e aprovação humana registradas em 2026-09-25, com promoção para `ready-for-dev`.

## Definition of Done

- [x] AC1–AC6 atendidos com evidências automatizadas e reais.
- [x] V3 é a fonte do schema, preserva V1/V2 e passa em PostgreSQL 16/Testcontainers sem H2.
- [x] Seeds BRL/USD coincidem exatamente com os valores, UUIDs, vigência e origem aprovados e são inequivocamente fictícios/reproduzíveis.
- [x] Consulta vigente, fronteiras, falhas e append-only estão cobertos sem cálculo ou snapshot antecipado.
- [x] `BaseRateQuery` é a única porta pública e retorna id, moeda, taxa mensal, vigência e origem; nenhuma escrita produtiva pública existe.
- [x] Origem obrigatória/não vazia/de até 64 caracteres é protegida no domínio e no PostgreSQL.
- [x] Nenhum `double`/`float`/arredondamento silencioso; domínio permanece livre de Spring/JPA/Jackson.
- [x] `spotless:check`, `verify`, ArchUnit, JaCoCo, regressão frontend e Compose passam.
- [x] DDL, ER, modelo, README, AI_USAGE, File List e evidências refletem a implementação real.
- [x] Gate documental e `git diff --check` passam; revisão não deixa Bloqueante/Importante aberto.
- [x] Story permanece em Review até aprovação humana final; Git mutável reservado à autora.

## Campos BMAD para implementação, revisão e evidências

### Dev Agent Record

- **Agente/modelo:** Codex (GPT-5), com revisão crítica BMAD.
- **Branch/baseline observada:** `feature/e1-s3-base-rates` / `50aa818` (story aprovada); baseline declarada preservada em `5c04a9ed4c1e1812b8a85358d2aa1110d878ce9e`.
- **Plano de implementação:** T1 → T6
- **Decisões locais/desvios:** o adapter JPA package-private implementa diretamente `BaseRateQuery`; a proposta intermediária de uma segunda porta pública de repositório foi removida para cumprir literalmente a superfície aprovada. A porta recebe o `CurrencyCode` canônico e traduz falhas de persistência em `BASE_RATE_QUERY_FAILED`. Não foi criado serviço pass-through, endpoint ou escrita produtiva.
- **Completion Notes:** V3, domínio puro, única porta de consulta, persistência JPA, provas PostgreSQL/ArchUnit, documentos e ambiente integrado concluídos. A imagem final aplicou V3 e expôs somente os endpoints anteriores. O agente não executou commits; posteriormente, a autora registrou a implementação nos commits `d635e5f`, `9501b0c`, `643832b` e `151228d`, validados pelo histórico e conteúdo. A correção final substituiu o proxy declarativo por `TransactionTemplate`: argumentos são validados antes da transação e falhas de abertura, execução e finalização são traduzidas para `BASE_RATE_QUERY_FAILED`. O agente também não executou os commits corretivos; eles foram realizados posteriormente pela autora em `852141a` e `b56a511`.
- **Riscos/dívidas remanescentes:** nenhuma dívida Bloqueante ou Importante conhecida na correção; os seeds são deliberadamente demonstrativos e as sugestões opcionais da revisão permanecem fora desta alteração.

### Evidências por critério

| AC | Status | Teste/comando/evidência |
|---|---|---|
| AC1 | Atendido | `BaseRateIntegrationTest` introspecta colunas, tipos, FK, checks, unique e índice na V3 real em PostgreSQL 16.6; `FlywayIntegrationTest` confirma a versão 3. Migration registrada pela autora em `d635e5f`. |
| AC2 | Atendido | Teste e smoke Compose confirmam os UUIDs, moedas, valores `NUMERIC(18,12)`, data e `DEMO_SEED` exatos para BRL/USD; schema/seeds em `d635e5f` e provas em `643832b`. |
| AC3 | Atendido | Testes cobrem fronteira inclusiva, data entre versões, versão mais recente e versão futura; o resultado preserva id, moeda, valor, vigência e origem. |
| AC4 | Atendido | Não existe porta/caso de uso de escrita; fixtures SQL provam histórico coexistente e unique impede sobrescrita lógica da mesma moeda/vigência. |
| AC5 | Atendido | `CurrencyCode` e `LocalDate` são validados antes do `TransactionTemplate`; testes com a implementação real do template provam tradução de falhas de abertura, execução e commit para `BASE_RATE_QUERY_FAILED`, sem exceção Spring/JPA atravessar a porta. A integração preserva `CURRENCY_NOT_SUPPORTED` e `BASE_RATE_NOT_FOUND`. Correção registrada pela autora em `852141a` e provas em `b56a511`. |
| AC6 | Atendido | Domínio usa `BigDecimal`, escala máxima 12 e nenhuma dependência de framework; ArchUnit e a prova de única porta pública passaram. |

### File List

| Operação | Arquivo | Motivo |
|---|---|---|
| Alterado | `_bmad-output/implementation-artifacts/e1-s3-disponibilizar-taxa-base-por-moeda-e-vigencia.md` | Execução, evidências e promoção para Review. |
| Criado | `backend/src/main/resources/db/migration/V3__create_base_rates.sql` | Schema versionado e seeds fictícios exatos. |
| Criado | `backend/src/main/java/com/srm/creditengine/currency/domain/port/BaseRate.java` | Resultado de domínio preciso e rastreável. |
| Criado | `backend/src/main/java/com/srm/creditengine/currency/domain/port/BaseRateQuery.java` | Única porta pública da capacidade. |
| Criado | `backend/src/main/java/com/srm/creditengine/currency/domain/port/BaseRateQueryException.java` | Tradução estável de falhas de persistência. |
| Criado | `backend/src/main/java/com/srm/creditengine/currency/domain/port/BaseRateCurrencyNotSupportedException.java` | Erro estável para catálogo ausente. |
| Criado | `backend/src/main/java/com/srm/creditengine/currency/domain/port/BaseRateNotFoundException.java` | Erro estável para ausência de versão aplicável. |
| Criado | `backend/src/main/java/com/srm/creditengine/currency/persistence/BaseRateEntity.java` | Mapeamento JPA package-private. |
| Criado | `backend/src/main/java/com/srm/creditengine/currency/persistence/JpaBaseRateRepository.java` | Consulta de vigência package-private. |
| Criado/alterado | `backend/src/main/java/com/srm/creditengine/currency/persistence/JpaBaseRateAdapter.java` | Adapter de consulta com validação pré-transação, `TransactionTemplate` read-only e tradução integral de falhas de infraestrutura. |
| Criado | `backend/src/test/java/com/srm/creditengine/currency/domain/port/BaseRateTest.java` | Bordas de domínio e precisão. |
| Criado | `backend/src/test/java/com/srm/creditengine/currency/BaseRateIntegrationTest.java` | Provas reais de schema, seeds, vigência, erros e append-only. |
| Criado/alterado | `backend/src/test/java/com/srm/creditengine/currency/persistence/JpaBaseRateAdapterTest.java` | Provas de validação antes da transação e tradução de falhas de abertura, execução e finalização. |
| Alterado | `backend/src/test/java/com/srm/creditengine/FlywayIntegrationTest.java` | Evidência de aplicação da V3. |
| Alterado | `backend/src/test/java/com/srm/creditengine/architecture/ArchitectureTest.java` | Guardrail da única porta pública. |
| Alterado | `backend/src/test/java/com/srm/creditengine/CreditEngineApplicationTest.java` | Isolamento do contexto HTTP sem persistência. |
| Alterado | `docs/database/ddl.sql`, `docs/database/er.md`, `docs/database/data-model.md` | Documentação derivada da V3. |
| Alterado | `README.md`, `AI_USAGE.md` | Uso interno, caráter fictício dos seeds e contribuição material de IA. |

**Commits executados posteriormente pela autora:**

- `d635e5f feat(db): add versioned base-rate configuration` — migration V3 e documentação do modelo de dados.
- `9501b0c feat(currency): add effective base-rate query` — domínio, porta e persistência da consulta.
- `643832b test(currency): prove base-rate persistence and contracts` — testes de domínio, integração, migration e arquitetura.
- `151228d docs(currency): document base-rate configuration` — atualização operacional do README.
- `852141a fix(currency): contain base-rate transaction failures` — fronteira programática e tradução integral do ciclo transacional.
- `b56a511 test(currency): prove transaction failure containment` — provas de validação pré-transação e falhas de abertura, execução e finalização.

### Testes e gates executados

| Data | Comando | Resultado | Evidência |
|---|---|---|---|
| 2026-09-25 | `mvnw.cmd -q spotless:check` | PASS | Formatação backend íntegra. |
| 2026-09-25 | `mvnw.cmd -q verify` | PASS | 76 testes em 14 suites; 0 falhas, erros ou skips; ArchUnit e PostgreSQL 16.6/Testcontainers verdes. |
| 2026-09-25 | JaCoCo no `verify` | PASS | 373/379 linhas (98,42%) e 79/90 branches (87,78%); classes novas de taxa base com 100% de linhas. |
| 2026-09-25 | `npm ci`, `lint`, `typecheck`, `test -- --run`, `build` | PASS | Instalação limpa em diretório temporário; 3 arquivos/14 testes; cobertura frontend 100% linhas e 87,5% branches; build Vite concluído. |
| 2026-09-25 | `docker compose config`, `up --build -d`, `ps` | PASS | PostgreSQL, WireMock, backend e frontend `healthy`; readiness/API, frontend e WireMock HTTP 200. |
| 2026-09-25 | Consulta SQL no PostgreSQL do Compose | PASS | Duas linhas exatas BRL/USD da V3; volume normal preservado. |
| 2026-09-25 | `/v3/api-docs` | PASS | Nenhum caminho de taxa base exposto. |
| 2026-09-25 | `check-docs.sh . story`, `git diff --check` | PASS | Gate documental e whitespace sem achados. |
| 2026-09-25 | Correção transacional: `spotless:check`, `verify` | PASS | 79 testes em 14 suites; 0 falhas, erros ou skips; ArchUnit e Testcontainers verdes; JaCoCo 98,44% linhas e 87,78% branches. |
| 2026-09-25 | Correção transacional: regressão frontend completa | PASS | `npm ci`, lint, typecheck, 14 testes e build passaram em cópia temporária isolada; 0 vulnerabilidades. |

### Review Record

- **Revisor/agente:** Codex (auto-revisão BMAD).
- **Base da revisão:** diff completo `origin/main...HEAD`, seis commits da E1-S3, código, testes, migration V3, documentação, ADRs 0001–0004 e checks remotos informados pela autora.
- **Achados Bloqueantes:** nenhum aberto.
- **Achados Importantes:** nenhum aberto — o achado transacional foi resolvido com validação anterior ao `TransactionTemplate` e tradução ao redor de todo o ciclo transacional; testes cobrem abertura, execução, rollback e commit.
- **Sugestões:** automatizar a ausência de endpoint no OpenAPI; considerar proteção append-only no usuário do banco somente quando futura story introduzir escrita administrativa. Nenhuma sugestão foi implementada nesta revisão.
- **Gates da correção:** Spotless, `verify` com 79 testes, ArchUnit, Testcontainers/PostgreSQL 16, regressão frontend e gate documental passaram.
- **Checks remotos:** backend, frontend e repository estavam aprovados no PR antes da correção, conforme confirmação da autora; os commits corretivos foram executados posteriormente pela autora, não pelo agente.
- **Limitações remanescentes:** a revisão não consultou diretamente o provedor do PR; não há endpoint intencionalmente, então a consulta interna é comprovada por integração PostgreSQL.
- **Recomendação:** correção local aprovada; manter em Review até os checks remotos da correção e a aprovação humana final.
- **Aprovação humana:** concedida pela autora em 2026-09-25, incluindo schema, seeds, semântica fracionária, origem rastreável e superfície pública somente de consulta.

### Change Log

| Data | Alteração | Autor/agente |
|---|---|---|
| 2026-09-25 | Story E1-S3 criada em Draft a partir dos artefatos de planejamento, ADRs aceitos e baseline concluída; nenhuma implementação realizada. | Codex |
| 2026-09-25 | Schema, seeds fictícios, semântica fracionária, origem e contrato exclusivamente de consulta aprovados humanamente; DoR concluída e story promovida para Ready for Dev. | Autora + Codex |
| 2026-09-25 | T1–T6 implementadas; gates locais, PostgreSQL 16 e Compose aprovados; segunda porta pública intermediária removida; story promovida para Review. | Codex |
| 2026-09-25 | Auto-revisão corrigiu tradução de persistência, fidelidade de `source`, contrato tipado e guardrail de portas; backend e Compose foram revalidados. | Codex |
| 2026-09-25 | A autora executou posteriormente os commits `d635e5f`, `9501b0c`, `643832b` e `151228d`; o agente apenas validou o histórico e registrou as evidências, sem executar Git mutável. | Autora + Codex |
| 2026-09-25 | Revisão final contra `origin/main` registrou checks remotos verdes e um achado Importante pendente na fronteira transacional/tradução de falhas de `BaseRateQuery`; story mantida em Review. | Codex |
| 2026-09-25 | Achado transacional corrigido com `TransactionTemplate`, validação prévia e testes de abertura/execução/finalização; gates locais completos aprovados e story mantida em Review. | Codex |
| 2026-09-25 | A autora executou posteriormente `852141a fix(currency): contain base-rate transaction failures` e `b56a511 test(currency): prove transaction failure containment`; nenhum commit foi executado pelo agente, e não restam achados Bloqueantes ou Importantes. | Autora + Codex |

### Handoff / próximo passo exato

Os commits corretivos já foram executados pela autora; confirmar os checks remotos da correção e realizar a aprovação humana final, mantendo a story em Review até confirmação explícita.

## Implementation Notes

## Spec Change Log

## Review Triage Log

| # | Veredito | Rota | Evidência |
|---|---|---|---|
| 1 | medium | patch — resolvido | Falhas do `EntityManager`/repository podiam atravessar a porta; `BaseRateQueryException` e teste focado agora traduzem `PersistenceException`/`DataAccessException`. |
| 2 | false | rejeitado | Escala acima de 12 é rejeitada no único caminho produtivo de construção; não existe escrita produtiva e SQL direto é exclusivamente fixture de teste. |
| 3 | medium | patch — resolvido | `trim()` alterava a origem persistida; o domínio agora valida `isBlank()` sem normalizar e o teste exige igualdade exata. |
| 4 | low | patch — resolvido junto | `String.length()` divergia de `VARCHAR` para caracteres suplementares; a validação agora usa code points. |
| 5 | medium | patch — resolvido | A porta duplicava moeda como `String`; agora recebe o `CurrencyCode` canônico e `LocalDate`, como o contrato aprovado. |
| 6 | medium | patch — resolvido | O guardrail filtrava nomes `BaseRate*`; agora compara o conjunto completo de interfaces públicas permitidas no pacote de portas. |
| 7 | false | rejeitado | O bloco congelado define append-only pela ausência de escrita produtiva pública e permite fixtures/package-private; não exige trigger ou usuário SQL imutável. |
| 8 | low | sugestão | A ausência de endpoint foi comprovada no OpenAPI da imagem final, mas não possui teste automatizado dedicado; não afeta o comportamento entregue. |
| 9 | low | patch — resolvido | Status textual ainda estava `In Progress`; foi sincronizado para `Review`. |
| 10 | medium | patch — resolvido | `@Transactional` foi removido do adapter; validação ocorre antes do `TransactionTemplate`, cujo ciclo completo fica dentro da tradução. Testes provam null sem interação transacional e falhas de abertura, execução/rollback e commit convertidas em `BASE_RATE_QUERY_FAILED`. |

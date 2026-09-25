---
title: 'E2-S1 — Aplicar Strategy por tipo'
type: 'feature'
created: '2026-09-25'
status: 'ready-for-dev'
route: 'full'
route_source: 'auto'
review: 'thorough'
review_source: 'pinned'
lenses_ran: []
review_loop_iteration: 0
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/_bmad-output/planning-artifacts/epics.md'
  - '{project-root}/_bmad-output/planning-artifacts/requirements-analysis.md'
  - '{project-root}/docs/adr/0001-adotar-monolito-modular-hexagonal.md'
  - '{project-root}/docs/adr/0002-adotar-postgresql-flyway-jpa-jooq.md'
  - '{project-root}/docs/adr/0003-padronizar-calculo-financeiro-decimal.md'
  - '{project-root}/docs/adr/0006-padronizar-contratos-e-erros-http.md'
  - '{project-root}/docs/adr/0007-adotar-observabilidade-e-resiliencia-seletiva.md'
---

# Story E2-S1 — Aplicar Strategy por tipo

- **Épico:** E2 — Precificação determinística
- **Status:** Ready for Dev
- **Prioridade:** Must
- **Predecessoras:** E0-S1, E0-S2, E1-S1, E1-S2 e E1-S3 concluídas

<frozen-after-approval reason="objetivo, critérios, decisões e limites pertencem à autora; mudanças exigem nova aprovação">

## Objetivo e valor

Introduzir no módulo `pricing` o catálogo persistido de tipos de recebível e a seleção de spread mensal por Strategy. O registry associa a `strategy_key` estável à implementação implantada, sem `if`/`switch` por tipo. E2-S1 resolve somente Strategy e spread; o cálculo de valor presente começa na E2-S2.

## Escopo e limites

**Incluído:** migration versionada de `receivable_type`; dois seeds reproduzíveis; consulta interna de tipo ativo; contrato `PricingStrategy`; `DuplicataMercantilPricingStrategy` e `ChequePreDatadoPricingStrategy`; registry sem fallback; spreads `BigDecimal` definidos pelas Strategies; erros estáveis; log e métrica para inconsistência de configuração; testes unitários, PostgreSQL 16/Testcontainers e ArchUnit; documentação derivada do schema.

**Fora de escopo:** coluna de spread no catálogo; nome de classe Java no banco; endpoint `GET /api/v1/receivable-types` ou qualquer outro endpoint; escrita administrativa; fórmula financeira, `FinancialMath`, ACT/30, calendário, taxa base aplicada, valor presente, deságio, arredondamento monetário, conversão cambial, simulação, settlement/snapshots e frontend. Não adicionar infraestrutura de observabilidade: reutilizar logging estruturado e Micrometer existentes.

## Rastreabilidade

| Fonte | Cobertura nesta story |
|---|---|
| RF-03 / CAP-02 | Catálogo inicial e seleção da regra de spread por Strategy. |
| RNF-01 | Spread com `BigDecimal`, sem `double`/`float` ou arredondamento. |
| RNF-04 | Falhas estáveis e mensagem pública segura, sem internals. |
| RNF-07 | Log estruturado e métrica de baixa cardinalidade para Strategy não configurada. |
| RNF-10 | Unidade e PostgreSQL 16/Testcontainers. |
| RNF-11 | Módulo/camadas e dependências verificadas por ArchUnit. |
| ADR-0001 | `pricing` hexagonal, domínio puro e dependências para dentro. |
| ADR-0002 | Flyway como fonte do schema, JPA e PostgreSQL real nos testes. |
| ADR-0003 | Strategy fornece spread mensal; fórmula permanece fora desta story. |
| ADR-0006 | Códigos estáveis; futuro HTTP 422 para tipo inativo e 500 para inconsistência interna. |
| ADR-0007 | Log estruturado e métrica sem identificadores de alta cardinalidade. |

## Schema, seeds e contrato interno aprovados

`receivable_type` contém `id UUID PRIMARY KEY`, `code VARCHAR(64) NOT NULL UNIQUE`, `name VARCHAR(100) NOT NULL`, `strategy_key VARCHAR(64) NOT NULL`, `active BOOLEAN NOT NULL` e `version BIGINT NOT NULL`. Checks impedem código, nome e chave vazios; `version >= 0`. O banco persiste somente a chave estável, nunca nome de classe Java. Não existe `spread_monthly`: os spreads pertencem às Strategies.

| id | code | name | strategy_key | active | version | Strategy | spread mensal |
|---|---|---|---|---:|---:|---|---:|
| `33333333-3333-4333-8333-333333333333` | `DUPLICATA_MERCANTIL` | `Duplicata Mercantil` | `DUPLICATA_MERCANTIL` | `true` | `0` | `DuplicataMercantilPricingStrategy` | `0.015` |
| `44444444-4444-4444-8444-444444444444` | `CHEQUE_PRE_DATADO` | `Cheque Pré-datado` | `CHEQUE_PRE_DATADO` | `true` | `0` | `ChequePreDatadoPricingStrategy` | `0.025` |

Spreads são frações decimais mensais construídas como `BigDecimal` a partir de texto. A consulta interna recebe o código do tipo, distingue ausente de inativo e entrega ao registry os dados necessários. O registry resolve por `strategy_key`; exatamente uma implementação deve existir. O resultado preserva identidade, código, chave e versão para uso auditável futuro, mas não calcula nem persiste preço.

Erros aprovados:

- tipo ausente: `RECEIVABLE_TYPE_NOT_FOUND`;
- tipo inativo: `RECEIVABLE_TYPE_INACTIVE`, com futuro HTTP `422`;
- tipo ativo cuja chave não possui Strategy implantada: `PRICING_STRATEGY_NOT_CONFIGURED`, inconsistência interna com futuro HTTP `500`, mensagem pública segura e nenhum cálculo;
- chave duplicada no registry: falha rápida de configuração na inicialização.

`PRICING_STRATEGY_NOT_CONFIGURED` gera log estruturado com `receivableTypeCode` e `strategyKey`, sem dados sensíveis, e incrementa `srm.pricing.strategy.resolution.failures` com `reason=not_configured`. O código e a chave não são tags da métrica para evitar alta cardinalidade.

## Critérios de aceite

### AC1 — Catálogo reproduzível

**Dado** PostgreSQL 16 vazio, **quando** Flyway aplicar V4, **então** `receivable_type` possuirá exatamente as colunas e constraints aprovadas e os dois seeds coincidirão em UUID, código, nome, chave, estado e versão, sem coluna de spread ou nome de classe.

### AC2 — Seleção por registry

**Dado** um tipo ativo cadastrado, **quando** a resolução interna ocorrer, **então** o registry selecionará exatamente uma Strategy pela `strategy_key`, sem `if`/`switch` por tipo, e retornará o spread definido pela implementação como `BigDecimal`.

### AC3 — Strategies iniciais

**Dada** Duplicata Mercantil, **quando** `DuplicataMercantilPricingStrategy` for aplicada, **então** o spread mensal será exatamente `0.015`; **dado** Cheque Pré-datado, **quando** `ChequePreDatadoPricingStrategy` for aplicada, **então** será `0.025`, sem tipo binário, arredondamento ou cálculo de VP.

### AC4 — Falhas determinísticas e seguras

**Dado** código malformado, tipo ausente ou inativo, **quando** houver resolução, **então** a operação falhará respectivamente por validação, `RECEIVABLE_TYPE_NOT_FOUND` ou `RECEIVABLE_TYPE_INACTIVE`; **dado** tipo ativo sem Strategy implantada, **então** falhará com `PRICING_STRATEGY_NOT_CONFIGURED`, mensagem pública segura, log/métrica aprovados e nenhum cálculo; exceções Spring/JPA não atravessarão a porta interna.

### AC5 — Limites arquiteturais

**Dado** o código de produção, **quando** ArchUnit executar, **então** o domínio de `pricing` não dependerá de Spring, JPA ou Jackson; `pricing` não acessará internals de `currency`, `settlement` ou `reporting`; e não haverá controller, endpoint ou porta produtiva pública de escrita nesta story.

### AC6 — Escopo e regressão

**Dado** o incremento concluído, **quando** os gates executarem, **então** não haverá fórmula, taxa base aplicada, simulação, conversão, liquidação ou frontend novo; V1–V3 e os contratos de E1 permanecerão verdes; V4, documentação, logs e métricas refletirão somente Strategy/spread.

## Matriz de casos normais, bordas e falhas

| Cenário | Estado/entrada | Resultado esperado |
|---|---|---|
| Duplicata | seed ativo e Strategy única | `BigDecimal("0.015")` |
| Cheque | seed ativo e Strategy única | `BigDecimal("0.025")` |
| Catálogo | seeds após migration em banco vazio | todos os campos exatos; nenhuma coluna de spread/classe |
| Código inválido | nulo, vazio, caixa ou formato inválido | validação antes da persistência |
| Tipo ausente | código válido não cadastrado | `RECEIVABLE_TYPE_NOT_FOUND` |
| Tipo inativo | cadastro existente com `active=false` | `RECEIVABLE_TYPE_INACTIVE`; nenhum registry/cálculo |
| Strategy ausente | tipo ativo com chave sem bean | `PRICING_STRATEGY_NOT_CONFIGURED`; futuro 500 seguro; um log e uma métrica; nenhum cálculo |
| Strategy duplicada | dois beans com a mesma chave | inicialização do registry falha rapidamente |
| Persistência indisponível | abertura, consulta ou finalização falha | erro estável; nenhuma exceção de infraestrutura escapa |
| Dado inválido no banco | campo obrigatório nulo/vazio, código duplicado ou versão negativa | constraint rejeita a escrita |
| Escopo negativo | resolução bem-sucedida | nenhuma consulta de taxa base/câmbio e nenhuma fórmula/persistência de preço |

</frozen-after-approval>

## Tarefas técnicas ordenadas

- [ ] **T1 — Migration:** criar `backend/src/main/resources/db/migration/V4__create_receivable_types.sql` com schema e seeds exatos aprovados, sem spread ou classe Java; preservar V1–V3.
- [ ] **T2 — Domínio/contratos internos:** criar em `backend/src/main/java/com/srm/creditengine/pricing/domain/**` o tipo, resultado, erros, `PricingStrategy` e a porta interna mínima de resolução, livres de framework e sem escrita pública.
- [ ] **T3 — Strategies/registry:** implementar as duas classes nomeadas, spreads `BigDecimal` exatos e registry por chave; rejeitar chave ausente/duplicada sem `if`/`switch` por tipo.
- [ ] **T4 — Persistência/composição:** criar em `pricing/persistence` entidade, repository e adapter de consulta; distinguir ausente/inativo e conter integralmente falhas transacionais.
- [ ] **T5 — Observabilidade:** na fronteira de composição/resolução, registrar uma vez a inconsistência `PRICING_STRATEGY_NOT_CONFIGURED` com os dois campos aprovados e incrementar a métrica de baixa cardinalidade; não logar falhas normais de tipo ausente/inativo como erro interno.
- [ ] **T6 — Testes:** cobrir toda a matriz com JUnit/AssertJ, PostgreSQL 16/Testcontainers, introspecção/constraints da V4, captura de log/métrica e fixtures ArchUnit; provar explicitamente que nenhum endpoint foi adicionado e que o spread não está no banco.
- [ ] **T7 — Documentação/regressão:** derivar DDL/ER/data model da V4; retirar/adiar o `GET /receivable-types` dos contratos previstos sem apresentá-lo como implementado; atualizar README, AI_USAGE e story somente com fatos; executar os gates completos.

## Arquivos previstos

- `backend/src/main/resources/db/migration/V4__create_receivable_types.sql`
- `backend/src/main/java/com/srm/creditengine/pricing/domain/**`
- `backend/src/main/java/com/srm/creditengine/pricing/persistence/**`
- `backend/src/main/java/com/srm/creditengine/pricing/service/**` — somente se necessário para a orquestração/observabilidade, sem pass-through vazio
- `backend/src/test/java/com/srm/creditengine/pricing/**`
- `backend/src/test/java/com/srm/creditengine/FlywayIntegrationTest.java`
- `backend/src/test/java/com/srm/creditengine/architecture/**`
- `docs/database/ddl.sql`, `docs/database/er.md`, `docs/database/data-model.md`
- `docs/api/contracts.md`, `README.md`, `AI_USAGE.md` e esta story

## Estratégia de testes e gates

- **Unitários:** contrato comum das Strategies, valores exatos, registry ausente/duplicado, mensagens seguras e nenhum cálculo.
- **Integração:** V4 em PostgreSQL 16 vazio; schema sem spread/classe, constraints, seeds exatos, tipo ativo/inativo/ausente e falhas transacionais contidas.
- **Arquitetura:** domínio puro, camadas, módulos, superfície pública deliberada, ausência de controller/escrita e fixtures negativas.
- **Observabilidade:** log contém `receivableTypeCode`/`strategyKey`; métrica usa apenas `reason=not_configured`; ambos ocorrem uma vez por resolução falha.
- **Compose:** quatro serviços existentes continuam healthy; Flyway chega à V4; smoke interno confirma ambas as Strategies sem endpoint novo.
- **Regressão:** backend e frontend existentes permanecem verdes.

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

## Requisitos de documentação

Flyway continua fonte normativa; DDL, ER e modelo de dados serão derivados da V4. README distinguirá catálogo persistido, chave da Strategy e spread em código, sem afirmar que E2-S2 já calcula preços. O contrato de API registrará que o catálogo REST foi adiado. AI_USAGE e evidências conterão somente trabalho e gates reais.

## Riscos e dependências

- **Atendidas:** cinco predecessoras `Done`; PostgreSQL/Flyway/Testcontainers, CI, Micrometer e guardrails disponíveis.
- **Divergência catálogo/código:** tipo ativo pode apontar para Strategy ausente; falhar com código 500 futuro, log e métrica, nunca fallback.
- **Strategy ornamental:** os spreads vivem nas implementações e o registry resolve comportamento; o banco mantém apenas identidade/configuração estável.
- **Mudança de spread:** exige alteração versionada de código e testes nesta solução aprovada; configuração dinâmica de spread está fora da story.
- **Acoplamento prematuro:** não consumir `BaseRateQuery`; não criar fórmula ou API de simulação.
- **Alta cardinalidade:** códigos/chaves ficam no log, não em tags métricas.
- **Rollback:** V4 é aditiva; correção posterior usa nova migration, nunca edição de migration aplicada.

## Definition of Ready

- [x] Objetivo, valor, precedência, rastreabilidade e limites definidos.
- [x] Schema, seeds, classes, spreads e contrato interno aprovados.
- [x] Erros de tipo ausente, inativo e Strategy não configurada aprovados.
- [x] Catálogo REST explicitamente adiado; ausência de endpoint faz parte do escopo.
- [x] Observabilidade, testes, riscos, arquivos e gates definidos.
- [x] E0-S1, E0-S2, E1-S1, E1-S2 e E1-S3 confirmadas como `Done`.
- [x] Draft aprovado humanamente e promovido a `ready-for-dev`.

**Resultado da DoR:** integralmente atendida em 2026-09-25; D1–D4 foram aprovadas humanamente e incorporadas ao bloco congelado, sem decisão pendente conhecida.

## Definition of Done

- [ ] AC1–AC6 atendidos com evidências reais.
- [ ] V4 validada estruturalmente e por inserções inválidas no PostgreSQL 16/Testcontainers; V1–V3 intactas.
- [ ] Seeds coincidem exatamente com os valores aprovados e não persistem spread/nome de classe.
- [ ] Strategies retornam `0.015` e `0.025` como `BigDecimal`; registry não usa fallback nem `if`/`switch` por tipo.
- [ ] Ausente, inativo, Strategy não configurada, duplicidade e infraestrutura possuem provas determinísticas; nenhum cálculo prossegue nas falhas.
- [ ] Log/métrica da inconsistência são seguros e de baixa cardinalidade.
- [ ] Domínio puro e limites passam no ArchUnit; nenhum endpoint, escrita pública ou escopo de E2-S2/E2-S3 foi antecipado.
- [ ] Spotless, verify/JaCoCo/ArchUnit, regressão frontend, Compose e healthchecks passam.
- [ ] DDL, ER, modelo, contratos, README, AI_USAGE, File List e evidências refletem o estado real.
- [ ] Gate documental e `git diff --check` passam; revisão não deixa achado Bloqueante/Importante aberto.
- [ ] Story termina no máximo em Review; Done depende de aprovação humana e checks remotos.

## Campos BMAD

### Dev Agent Record

- **Agente/modelo:** a preencher na implementação.
- **Branch/baseline:** a preencher após inspeção humana da branch de trabalho.
- **Completion Notes:** vazias até implementação.

### File List

- `_bmad-output/implementation-artifacts/e2-s1-aplicar-strategy-por-tipo.md` — criação e refinamento do artefato aprovado.

### Evidências

Nenhuma evidência de implementação foi produzida nesta preparação documental.

### Review Record

- **Status:** planejamento aprovado; implementação/revisão ainda não iniciadas.
- **Achados:** nenhum achado de implementação nesta fase.
- **Aprovação humana:** D1–D4 e promoção para Ready for Dev aprovadas pela autora em 2026-09-25.

### Change Log

| Data | Alteração | Autor/agente |
|---|---|---|
| 2026-09-25 | Draft criado a partir do roadmap, requisitos, ADRs aceitos e baseline E1 concluída; D1–D4 mantidas abertas para decisão humana. | Codex |
| 2026-09-25 | D1–D4 aprovadas: catálogo/seeds, spreads nas Strategies, erros, observabilidade e adiamento do REST sincronizados; DoR concluída e story promovida para Ready for Dev. | Autora + Codex |

## Implementation Notes

## Spec Change Log

## Review Triage Log

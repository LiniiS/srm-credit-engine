---
title: 'E2-S1 — Aplicar Strategy por tipo'
type: 'feature'
created: '2026-09-25'
status: 'in-review'
baseline_commit: 'aaea0b65178dc8499c781f38cd07ad88c1c3129b'
route: 'full'
route_source: 'auto'
review: 'thorough'
review_source: 'pinned'
lenses_ran:
  - 'blind-hunter'
  - 'edge-case-hunter'
  - 'verification-gap'
  - 'intent-alignment'
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
- **Status:** Review
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

- [x] **T1 — Migration:** criar `backend/src/main/resources/db/migration/V4__create_receivable_types.sql` com schema e seeds exatos aprovados, sem spread ou classe Java; preservar V1–V3.
- [x] **T2 — Domínio/contratos internos:** criar em `backend/src/main/java/com/srm/creditengine/pricing/domain/**` o tipo, resultado, erros, `PricingStrategy` e a porta interna mínima de resolução, livres de framework e sem escrita pública.
- [x] **T3 — Strategies/registry:** implementar as duas classes nomeadas, spreads `BigDecimal` exatos e registry por chave; rejeitar chave ausente/duplicada sem `if`/`switch` por tipo.
- [x] **T4 — Persistência/composição:** criar em `pricing/persistence` entidade, repository e adapter de consulta; distinguir ausente/inativo e conter integralmente falhas transacionais.
- [x] **T5 — Observabilidade:** na fronteira de composição/resolução, registrar uma vez a inconsistência `PRICING_STRATEGY_NOT_CONFIGURED` com os dois campos aprovados e incrementar a métrica de baixa cardinalidade; não logar falhas normais de tipo ausente/inativo como erro interno.
- [x] **T6 — Testes:** cobrir toda a matriz com JUnit/AssertJ, PostgreSQL 16/Testcontainers, introspecção/constraints da V4, captura de log/métrica e fixtures ArchUnit; provar explicitamente que nenhum endpoint foi adicionado e que o spread não está no banco.
- [x] **T7 — Documentação/regressão:** derivar DDL/ER/data model da V4; retirar/adiar o `GET /receivable-types` dos contratos previstos sem apresentá-lo como implementado; atualizar README, AI_USAGE e story somente com fatos; executar os gates completos.

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

- [x] AC1–AC6 atendidos com evidências reais.
- [x] V4 validada estruturalmente e por inserções inválidas no PostgreSQL 16/Testcontainers; V1–V3 intactas.
- [x] Seeds coincidem exatamente com os valores aprovados e não persistem spread/nome de classe.
- [x] Strategies retornam `0.015` e `0.025` como `BigDecimal`; registry não usa fallback nem `if`/`switch` por tipo.
- [x] Ausente, inativo, Strategy não configurada, duplicidade e infraestrutura possuem provas determinísticas; nenhum cálculo prossegue nas falhas.
- [x] Log/métrica da inconsistência são seguros e de baixa cardinalidade.
- [x] Domínio puro e limites passam no ArchUnit; nenhum endpoint, escrita pública ou escopo de E2-S2/E2-S3 foi antecipado.
- [x] Spotless, verify/JaCoCo/ArchUnit, regressão frontend, Compose e healthchecks passam.
- [x] DDL, ER, modelo, contratos, README, AI_USAGE, File List e evidências refletem o estado real.
- [x] Gate documental e `git diff --check` passam; revisão não deixa achado Bloqueante/Importante aberto.
- [x] Story termina no máximo em Review; Done depende de aprovação humana e checks remotos.

## Campos BMAD

### Dev Agent Record

- **Agente/modelo:** Codex (implementação assistida por IA; nenhum commit executado pelo agente).
- **Branch/baseline:** `feature/e2-s1-pricing-strategies` sobre `aaea0b65178dc8499c781f38cd07ad88c1c3129b`.
- **Completion Notes:** V4, catálogo interno, duas Strategies, registry extensível, resolução transacional segura, observabilidade e documentação concluídos. A revisão fortaleceu a prova explícita de códigos inválidos e da estrutura real do schema. O agente não executou commits; posteriormente, a autora registrou a implementação nos commits `f9d426a`, `7275d6a`, `5e82330` e `297939f`. A story permanece em Review para aprovação humana e CI remota.

### File List

- `backend/src/main/resources/db/migration/V4__create_receivable_types.sql`
- `backend/src/main/java/com/srm/creditengine/pricing/domain/PricingStrategy.java`
- `backend/src/main/java/com/srm/creditengine/pricing/domain/PricingStrategyNotConfiguredException.java`
- `backend/src/main/java/com/srm/creditengine/pricing/domain/ReceivableType.java`
- `backend/src/main/java/com/srm/creditengine/pricing/domain/ReceivableTypeCode.java`
- `backend/src/main/java/com/srm/creditengine/pricing/domain/ReceivableTypeInactiveException.java`
- `backend/src/main/java/com/srm/creditengine/pricing/domain/ReceivableTypeNotFoundException.java`
- `backend/src/main/java/com/srm/creditengine/pricing/domain/ReceivableTypeQueryException.java`
- `backend/src/main/java/com/srm/creditengine/pricing/domain/ResolvedPricingStrategy.java`
- `backend/src/main/java/com/srm/creditengine/pricing/domain/port/ReceivableTypeCatalog.java`
- `backend/src/main/java/com/srm/creditengine/pricing/domain/port/ReceivableTypePricingResolver.java`
- `backend/src/main/java/com/srm/creditengine/pricing/persistence/JpaReceivableTypeCatalog.java`
- `backend/src/main/java/com/srm/creditengine/pricing/persistence/JpaReceivableTypeRepository.java`
- `backend/src/main/java/com/srm/creditengine/pricing/persistence/ReceivableTypeEntity.java`
- `backend/src/main/java/com/srm/creditengine/pricing/service/ChequePreDatadoPricingStrategy.java`
- `backend/src/main/java/com/srm/creditengine/pricing/service/DuplicataMercantilPricingStrategy.java`
- `backend/src/main/java/com/srm/creditengine/pricing/service/PricingStrategyRegistry.java`
- `backend/src/main/java/com/srm/creditengine/pricing/service/ReceivableTypePricingService.java`
- `backend/src/test/java/com/srm/creditengine/CreditEngineApplicationTest.java`
- `backend/src/test/java/com/srm/creditengine/FlywayIntegrationTest.java`
- `backend/src/test/java/com/srm/creditengine/architecture/ArchitectureTest.java`
- `backend/src/test/java/com/srm/creditengine/pricing/ReceivableTypeIntegrationTest.java`
- `backend/src/test/java/com/srm/creditengine/pricing/persistence/JpaReceivableTypeCatalogTest.java`
- `backend/src/test/java/com/srm/creditengine/pricing/service/PricingStrategyTest.java`
- `backend/src/test/java/com/srm/creditengine/pricing/service/ReceivableTypePricingServiceTest.java`
- `docs/api/contracts.md`
- `docs/database/data-model.md`
- `docs/database/ddl.sql`
- `docs/database/er.md`
- `README.md`
- `AI_USAGE.md`
- `_bmad-output/implementation-artifacts/e2-s1-aplicar-strategy-por-tipo.md`

A File List acima corresponde ao conteúdo confirmado dos quatro commits humanos da E2-S1; `AI_USAGE.md` e esta story permanecem como atualização documental ainda não commitada.

### Evidências

| AC | Evidência real |
|---|---|
| AC1 | V4 aplicada em PostgreSQL 16/Testcontainers; introspecção confirma seis colunas, tipos, tamanhos e nulabilidade; constraints rejeitam campos vazios, código duplicado e versão negativa; seeds conferidos também no Compose. |
| AC2 | `PricingStrategyRegistry` indexa beans por `key`, rejeita duplicidade/ausência e aceita uma Strategy de teste nova sem alteração do registry; não existe seleção por tipo com `if`/`switch`. |
| AC3 | Testes comprovam `BigDecimal("0.015")` e `BigDecimal("0.025")`, sem `double`/`float`, fórmula ou arredondamento. |
| AC4 | Testes cobrem código nulo, vazio, caixa/formato inválidos antes do catálogo; ausente, inativo, Strategy ausente, log/métrica e falhas de abertura/execução/finalização da transação traduzidas. |
| AC5 | ArchUnit mantém domínio puro e limites de módulos; teste de superfície confirma somente as duas portas internas aprovadas e nenhum controller; Compose/OpenAPI confirmam `GET /api/v1/receivable-types` ausente (HTTP 404). |
| AC6 | Backend: 102 testes verdes, JaCoCo 98,13% linhas e 86,36% branches. Frontend: 14 testes verdes e build. Compose: PostgreSQL, WireMock, backend e frontend healthy. Documentação derivada da V4. |

**Gates executados:** `spotless:check` passou; `verify` passou com 18 suites/102 testes, zero falhas/erros/skips, incluindo ArchUnit e Testcontainers; JaCoCo registrou 98,13% de linhas e 86,36% de branches; frontend limpo passou `npm ci`, lint, typecheck, 14 testes e build; `docker compose config` e `up --build -d` passaram; quatro serviços ficaram healthy; readiness retornou `{"status":"UP"}`; frontend retornou HTTP 200; seeds exatos foram consultados no PostgreSQL; o endpoint adiado retornou 404 e não aparece no OpenAPI. O `npm ci` direto no workspace encontrou um binário nativo bloqueado pelo editor, portanto a regressão limpa foi executada numa cópia temporária das mesmas fontes e lockfile; o build da imagem também executou `npm ci` no contexto do projeto sem erro.

**Rastreabilidade dos commits humanos:** `f9d426a` contém catálogo/migration e documentação derivada do schema; `7275d6a` contém domínio, portas, persistência, Strategies e registry; `5e82330` contém as provas unitárias, PostgreSQL/Testcontainers e arquitetura; `297939f` contém README e contrato documental. Todos foram executados posteriormente pela autora, nunca pelo agente.

### Review Record

- **Status:** implementação concluída e revisada; aguardando aprovação humana e checks remotos.
- **Bloqueantes:** nenhum.
- **Importantes:** nenhum aberto. Durante a revisão, foram corrigidos validação fail-fast de chaves do registry, isolamento de falha da métrica, introspecção exata das constraints e detecção arquitetural de controllers por anotação; as provas de formatos inválidos, tipo inativo e metadados estruturais da V4 também foram ampliadas.
- **Sugestões:** estabilizar futuramente o teste legado `HttpExchangeRateProviderTest.retries_only_allowlisted_server_errors`, que falhou uma vez de forma transitória e passou isoladamente e no `verify` integral subsequente; não pertence ao diff funcional de E2-S1.
- **Recomendação:** pronta para revisão humana/CI; não marcar Done antes dessas aprovações.
- **Aprovação humana:** D1–D4 e promoção para Ready for Dev aprovadas pela autora em 2026-09-25.
- **Commits humanos confirmados:** `f9d426a`, `7275d6a`, `5e82330` e `297939f`, executados posteriormente pela autora; o agente não realizou operações Git mutáveis.

### Change Log

| Data | Alteração | Autor/agente |
|---|---|---|
| 2026-09-25 | Draft criado a partir do roadmap, requisitos, ADRs aceitos e baseline E1 concluída; D1–D4 mantidas abertas para decisão humana. | Codex |
| 2026-09-25 | D1–D4 aprovadas: catálogo/seeds, spreads nas Strategies, erros, observabilidade e adiamento do REST sincronizados; DoR concluída e story promovida para Ready for Dev. | Autora + Codex |
| 2026-09-25 | E2-S1 implementada e verificada localmente; autorrevisão sem achados Bloqueantes ou Importantes abertos; status movido para Review. | Codex |
| 2026-09-25 | Revisão aprofundada corrigiu guardrails do registry/telemetria/schema/controller e repetiu `verify` com 102 testes verdes; nenhum achado Bloqueante ou Importante permaneceu. | Codex |
| 2026-09-25 | Commits humanos `f9d426a`, `7275d6a`, `5e82330` e `297939f` confirmados no histórico e vinculados às respectivas mudanças; commits executados posteriormente pela autora, não pelo agente. | Autora + Codex |

## Implementation Notes

## Spec Change Log

## Review Triage Log

| # | Origem | Veredito | Rota/evidência |
|---|---|---|---|
| 1 | blind-hunter — invariantes de `ResolvedPricingStrategy` | false | O resultado produtivo só é criado pelo resolver após `ReceivableType` validar chave/versão e a Strategy fornecer spread; construção manual inválida não é caminho exercitável do caso de uso. |
| 2 | blind-hunter — chave nula/vazia no registry | medium | `PricingStrategyRegistry` aceitava chave vazia e falhava incidentalmente para nula. Corrigido com validação fail-fast determinística e testes para lista, Strategy e chave inválidas. |
| 3 | blind-hunter — falha de dado tratada como consulta | false | A fronteira deve conter qualquer falha de materialização/persistência como `RECEIVABLE_TYPE_QUERY_FAILED`; distinguir corrupção criaria novo contrato não aprovado e não expõe internals. |
| 4 | blind-hunter — causa de infraestrutura preservada | false | A exceção que atravessa a porta é exclusivamente o tipo de domínio com mensagem estável; a causa fica encadeada para diagnóstico interno e não há endpoint nesta story. |
| 5 | blind-hunter — falha da métrica mascara erro | medium | Uma falha do `MeterRegistry` podia substituir `PRICING_STRATEGY_NOT_CONFIGURED`. Corrigido isolando a telemetria e comprovando a preservação do erro funcional. |
| 6 | blind-hunter — constraints não introspectadas integralmente | medium | A prova era insuficiente para a alegação de schema exato. Corrigido com introspecção nominal de todas as constraints e inserção nula adicional, além das violações já cobertas. |
| 7 | blind-hunter — ausência de controller verificada só por nome | medium | Uma classe anotada com outro nome escaparia. Corrigido com verificação ArchUnit adicional para `@Controller` e `@RestController`; smoke/OpenAPI continuam provando ausência externa. |
| 8 | blind-hunter — tipo exato da validação malformada | false | AC4 exige rejeição antes da persistência, não um código público para validação interna; o teste comprova a ordem e aceita os dois tipos Java apropriados a nulo versus formato. |
| 9 | blind-hunter — inativo sem prova de não execução | low | A ordem do serviço já impedia a execução; adicionada prova direta com Strategy sentinela para eliminar inferência. |
| 10 | blind-hunter — `npm ci` fora do workspace | false | As mesmas fontes/lockfile passaram em cópia limpa e o Dockerfile executou `npm ci` no contexto do workspace durante o build; o bloqueio local é do binário carregado pelo editor, não do repositório. |
| 11 | intent-alignment — integração a fluxo funcional | false | O bloco congelado define resolução interna e exclui cálculo/endpoint até E2-S2; o diff implementa exatamente essa leitura aprovada. |

As lentes `edge-case-hunter` e `verification-gap` foram lançadas, mas não conseguiram ler seus prompts renderizados por restrição de acesso do ambiente e encerraram sem achados. A revisão manual e as demais lentes cobriram o diff; essa limitação operacional permanece registrada sem alterar o resultado técnico.

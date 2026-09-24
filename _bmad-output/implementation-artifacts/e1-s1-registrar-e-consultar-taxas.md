---
title: 'E1-S1 — Registrar e consultar taxas'
type: 'feature'
created: '2026-09-23'
status: 'done'
baseline_commit: '6445775d9f0387ed36005e2d21205010053208b1'
route: 'full'
route_source: 'auto'
review: 'thorough'
review_source: 'pinned'
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']
review_loop_iteration: 0
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/docs/adr/0001-adotar-monolito-modular-hexagonal.md'
  - '{project-root}/docs/adr/0002-adotar-postgresql-flyway-jpa-jooq.md'
  - '{project-root}/docs/adr/0004-padronizar-cambio-e-vigencia.md'
  - '{project-root}/docs/adr/0006-padronizar-contratos-e-erros-http.md'
---

# Story E1-S1 — Registrar e consultar taxas

- **Épico:** E1 — Câmbio auditável
- **Status:** Done
- **Prioridade:** Must
- **Predecessoras:** E0-S1 e E0-S2 concluídas

<frozen-after-approval reason="objetivo, critérios e limites pertencem à autora; mudanças exigem nova aprovação">

## Objetivo e valor

Permitir que o operador registre uma taxa de câmbio USD/BRL e consulte a versão vigente do par, preservando cada registro como evidência auditável. O incremento inaugura o módulo `currency` sobre a baseline validada, sem sincronização externa ou cálculo financeiro.

## Requisitos e rastreabilidade

| Fonte | Cobertura |
|---|---|
| E1 / E1-S1; CAP-01 | Registro append-only e consulta por par/vigência. |
| RF-01 | Cadastrar e consultar taxa com fonte e instante efetivo. |
| RNF-01 | Valor da taxa decimal; nenhum tipo binário no fluxo financeiro. |
| RNF-04 | Entrada validada e erro seguro, sem internals. |
| RNF-10 | Testes unitários e integração PostgreSQL 16/Testcontainers. |
| RNF-11 | Limites de módulo/camadas protegidos por ArchUnit. |
| ADR-0001 | `currency` em `api → service → domain ← persistence`, dependente apenas de `shared`. |
| ADR-0002 | Flyway como fonte do schema, JPA na escrita e PostgreSQL real nos testes. |
| ADR-0004 | BASE/QUOTE explícito, histórico append-only e seleção por vigência. |
| ADR-0006 | REST `/api/v1`, DTOs, decimal como string e RFC 9457 `ProblemDetail`. |

## Critérios de aceite

### AC1 — Registrar uma nova versão

**Dado** `baseCurrency=USD`, `quoteCurrency=BRL`, `rate="5.10000000"`, fonte não vazia e `effectiveAt` ISO-8601 válido
**Quando** `POST /api/v1/exchange-rates` for chamado
**Então** a API retorna `201 Created`, `Location` e um DTO com id, par, taxa, fonte, vigência e criação
**E** `effectiveAt` e `createdAt` são instantes UTC persistidos como `TIMESTAMPTZ` e expostos em ISO-8601
**E** `createdAt` é gerado pelo servidor por `Clock` injetável, não é aceito no request
**E** a taxa é persistida como decimal, sem conversão binária.

### AC2 — Histórico é append-only

**Dado** que já existe uma taxa para USD/BRL
**Quando** outra taxa válida do mesmo par é registrada
**Então** um novo id é criado e a versão anterior permanece inalterada
**E** não existe endpoint nem operação produtiva de update/delete de taxa.

### AC3 — Consultar a taxa vigente

**Dadas** versões passadas e futuras de um par suportado
**Quando** `GET /api/v1/exchange-rates/latest?base=USD&quote=BRL` for chamado
**Então** a API retorna `200` com a primeira versão aplicável segundo `effective_at DESC, created_at DESC, id DESC`, considerando apenas `effective_at <= Clock.instant()` do servidor
**E** uma taxa futura permanece armazenada, mas não é vigente antes de seu `effectiveAt`
**E** a resposta preserva a convenção `BASE/QUOTE` sem calcular conversão.

### AC4 — Par sem versão vigente

**Dado** um par formado por moedas presentes no catálogo, mas sem taxa aplicável no instante consultado
**Quando** a consulta vigente for feita
**Então** a API retorna `404 application/problem+json` com código `EXCHANGE_RATE_NOT_FOUND`
**E** não expõe stack trace, SQL ou mensagem interna.

### AC5 — Entradas inválidas são rejeitadas com segurança

**Dada** taxa nula, zero, negativa, com escala acima de 8, moeda fora do formato de três letras maiúsculas, moedas iguais, fonte vazia ou instante malformado
**Quando** o comando ou a consulta alcançar a API
**Então** a resposta é `400 application/problem+json` com código `VALIDATION_ERROR` e violações identificáveis por campo
**E** nenhum registro é criado.

### AC6 — Catálogo de moedas e persistência são verificáveis

**Dado** um banco PostgreSQL 16 vazio
**Quando** Flyway executar a migration V2
**Então** são criadas `currency` e `exchange_rate`, e USD/BRL são inseridas como dados de referência do catálogo necessários ao MVP
**E** esses registros não são os seeds fictícios de taxa base, que pertencem exclusivamente à E1-S3
**E** `exchange_rate` usa FKs, `NUMERIC(18,8)`, `effective_at TIMESTAMPTZ`, `created_at TIMESTAMPTZ`, checks e índice `(base_currency, quote_currency, effective_at DESC, created_at DESC, id DESC)`
**E** um código bem formatado, mas ausente do catálogo, retorna `400 application/problem+json` com código `CURRENCY_NOT_SUPPORTED`
**E** `mvn verify` comprova o schema, catálogo, append-only, ordenação total, erros e guardrails em PostgreSQL 16 via Testcontainers.

## Limites de escopo

**Incluído:** catálogo com USD/BRL como dados de referência necessários ao MVP; migration versionada; domínio/porta/caso de uso/adaptador JPA do módulo `currency`; POST e GET latest no instante atual do servidor; validação; `ProblemDetail`; testes e documentação afetada.

**Excluído:** edição/exclusão/listagem histórica; sincronização ou provider mock (E1-S2); timeout/retry/circuit breaker; taxa base/seeds (E1-S3); regra de expiração de 15 minutos para consumo financeiro; conversão, pricing, settlement, reporting e UI de câmbio; autenticação; observabilidade de E6; H2, Supabase, Kafka, Redis e microserviços.

## Matriz de comportamento e bordas

| Cenário | Estado/entrada | Resultado |
|---|---|---|
| Primeira taxa | USD/BRL válida e vigente | `201`; registro consultável. |
| Nova versão | Mesmo par, outro valor/instante | Novo registro; anterior intacto. |
| Versão futura | Uma passada e outra futura | GET retorna somente a passada. |
| Instante vigente | `Clock` fixo no servidor | Seleção considera somente `effectiveAt <= Clock.instant()`. |
| Empate temporal | Mesmo `effectiveAt` e, quando possível, mesmo `createdAt` | Vence `effective_at DESC, created_at DESC, id DESC`. |
| Sem versão vigente | Moedas catalogadas, par sem versão ou apenas futura | `404`, código `EXCHANGE_RATE_NOT_FOUND`. |
| Moeda não suportada | Código bem formatado ausente do catálogo | `400`, código `CURRENCY_NOT_SUPPORTED`. |
| Entrada inválida | Par malformado/igual, taxa inválida, fonte vazia ou instante inválido | `400`, código `VALIDATION_ERROR`; nenhuma escrita. |

</frozen-after-approval>

## Tarefas técnicas ordenadas

- [x] **T1 — Contrato e domínio (AC1–AC5):** criar tipos e portas em `backend/src/main/java/com/srm/creditengine/currency/domain/`, mantendo taxa em `BigDecimal`, tempos em `Instant`/UTC, par explícito e `Clock` injetável; `createdAt` nunca entra no comando do cliente; testar invariantes sem Spring.
- [x] **T2 — Schema e catálogo (AC2, AC6):** criar `V2__create_exchange_rates.sql` com `currency`/`exchange_rate`, USD/BRL como referências do catálogo, `TIMESTAMPTZ`, constraints e índice total; não incluir seeds de taxa base nem alterar V1; atualizar DDL/ER derivados.
- [x] **T3 — Persistência (AC1–AC4, AC6):** adicionar JPA e implementar adapter/repositório em `currency/persistence`; selecionar `effective_at <= Clock.instant()` por `effective_at DESC, created_at DESC, id DESC`; entidades não vazam da camada.
- [x] **T4 — Caso de uso (AC1–AC6):** implementar registro transacional e consulta read-only em `currency/service`, distinguindo validação sintática, moeda não suportada e ausência de taxa vigente, sem update/delete ou chamada externa.
- [x] **T5 — API (AC1, AC3–AC6):** implementar DTOs/controllers em `currency/api`, sem parâmetro público de instante, e handler `ProblemDetail` com `VALIDATION_ERROR`, `CURRENCY_NOT_SUPPORTED` e `EXCHANGE_RATE_NOT_FOUND`, alinhado a ADR-0006.
- [x] **T6 — Provas (AC1–AC6):** adicionar testes unitários, MockMvc/contrato e integração Testcontainers PostgreSQL 16; provar catálogo, append-only, UTC, tempo controlado, taxa futura, ordenação total inclusive mesmo `effectiveAt` e, quando possível, mesmo `createdAt`, códigos de erro e ausência de vazamento interno.
- [x] **T7 — Documentação e fechamento:** atualizar somente README, DDL/ER, AI_USAGE e esta story com fatos reais; executar gates e revisar o diff contra os limites.

## Code Map e arquivos previstos

| Caminho | Ação prevista |
|---|---|
| `backend/pom.xml` | Adicionar JPA, Bean Validation e OpenAPI somente se necessários ao contrato. |
| `backend/src/main/java/com/srm/creditengine/currency/{api,service,domain,persistence}/**` | Primeiro corte funcional do módulo `currency`. |
| `backend/src/main/resources/db/migration/V2__create_exchange_rates.sql` | Catálogo USD/BRL e schema append-only com tempos UTC e índice total. |
| `backend/src/test/java/com/srm/creditengine/currency/**` | Testes unitários, HTTP e Testcontainers. |
| `docs/api/contracts.md` | Alinhar o GET latest aos parâmetros públicos `base` e `quote` e ao `Clock` do servidor. |
| `docs/database/ddl.sql`, `docs/database/er.md` | Derivação fiel da migration. |
| `README.md`, `AI_USAGE.md`, esta story | Contrato, evidências e uso material de IA. |

Reutilizar `FlywayIntegrationTest`, configuração de datasource, CI e regras ArchUnit existentes. Não modificar a migration V1, relaxar guardrails, criar feature frontend vazia ou duplicar o tratamento de erro em cada controller.

## Estratégia de testes e gates

- **Domínio:** par, valor, escala e fonte; `Clock` fixo; nenhum contexto Spring.
- **Persistência:** migrations em banco vazio, catálogo USD/BRL, duas versões preservadas, futura ignorada e ordenação `effective_at DESC, created_at DESC, id DESC`, inclusive empates controlados, em PostgreSQL 16 real.
- **HTTP:** `201/Location`, `200`, `400` e `404`; GET latest aceita somente `base` e `quote`; códigos estáveis, media type e campos seguros de `ProblemDetail`; `violations` por campo quando aplicável e decimal como string, conforme ADR-0006.
- **Regressão:** ArchUnit/JaCoCo, frontend e Compose continuam verdes.

```bash
cd backend && ./mvnw -q spotless:check && ./mvnw -q verify
cd frontend && npm ci && npm run lint && npm run typecheck && npm run test -- --run && npm run build
docker compose config
docker compose up --build -d
docker compose ps
curl -i -X POST http://localhost:8080/api/v1/exchange-rates ...
curl -i 'http://localhost:8080/api/v1/exchange-rates/latest?base=USD&quote=BRL'
bash .agents/skills/srm-documentacao/scripts/check-docs.sh . story
git diff --check
```

## Riscos, dependências e pré-condições

- **Pré-condições atendidas:** E0-S1/E0-S2 `Done`; ADRs aceitos; baseline e CI verdes.
- **Precisão/serialização:** configuração acidental pode emitir JSON number; testar o JSON literal.
- **Seleção temporal:** relógio real gera teste instável; usar `Clock` controlado e ordenação total.
- **Catálogo versus taxa base:** USD/BRL são referências de moeda do MVP; não inserir nesta story valores fictícios de taxa base de E1-S3.
- **Append-only:** métodos genéricos de repositório podem permitir alteração; não expor operação de update/delete no caso de uso e provar que nova taxa gera novo id.
- **Migração:** V2 deve evoluir V1 sem reescrevê-la; Testcontainers valida banco vazio.
- **Contrato global:** E1-S1 entrega apenas os erros exigidos por suas rotas; padronização integral permanece em E6-S1.

## Definition of Ready

- [x] Objetivo único, valor, predecessoras e rastreabilidade definidos.
- [x] ACs verificáveis em Given/When/Then e bordas conhecidas.
- [x] Contrato REST e schema previstos pela arquitetura/ADRs aceitos.
- [x] Estratégia de testes e riscos de precisão, tempo, migração e segurança definidos.
- [x] Escopo cabe em um incremento vertical sem depender de E1-S2/E1-S3.
- [x] Aprovação humana e promoção para `ready-for-dev`.

**Resultado da DoR:** atendida; aprovação humana concedida em 2026-09-23 e story promovida para `ready-for-dev`.

## Definition of Done

- [x] AC1–AC6 atendidos com evidências reais.
- [x] Backend `spotless:check` e `verify` passam; cobertura de produção atende ao threshold vigente.
- [x] Testcontainers PostgreSQL 16 comprova migration e persistência; nenhum H2.
- [x] Catálogo contém USD/BRL como referência, sem seeds de taxa base; moeda não suportada e par sem taxa vigente mantêm semânticas distintas.
- [x] Frontend e Compose passam como regressão; smoke POST/GET real é registrado.
- [x] OpenAPI/DTOs/`ProblemDetail`, códigos estáveis, DDL e ER refletem o comportamento implementado e `docs/api/contracts.md`/ADR-0006.
- [x] `effectiveAt`/`createdAt` usam `Instant`/UTC e `TIMESTAMPTZ`; ambos são normalizados para micros antes da persistência, `createdAt` é produzido pelo servidor, e consulta/testes usam ordenação total.
- [x] Sem `double`/`float`, segredo, internals em erros ou funcionalidade fora do escopo.
- [x] Documentação, File List, Completion Notes, evidências e AI_USAGE atualizados com fatos reais.
- [x] Revisão sem achado Bloqueante/Importante aberto e aprovação humana final.
- [x] Plano de commits preparado; Git mutável reservado somente à autora.

## Campos BMAD para implementação, revisão e evidências

### Dev Agent Record

- **Agente/modelo:** Codex (GPT-5)
- **Branch/baseline observada:** `feature/e1-s1-exchange-rates` / `6445775d9f0387ed36005e2d21205010053208b1`
- **Plano de implementação:** T1 → T7
- **Decisões locais / desvios:**
- **Completion Notes:** Implementado o módulo `currency` vertical com migration V2, catálogo USD/BRL, BigDecimal, JPA append-only, seleção temporal total, RFC 9457/OpenAPI e testes PostgreSQL 16. A revisão corrigiu a documentação OpenAPI, a identificação segura de campos inválidos e a precisão temporal para micros. Os três achados Importantes finais foram resolvidos: moedas iguais agora produzem violação determinística em `quoteCurrency` sem escrita; a migration é verificada estruturalmente e por inserções inválidas em PostgreSQL real; e o OpenAPI referencia o schema efetivo de `ProblemDetail`, sem `traceId` fictício. Imagens reconstruídas, três serviços healthy e regressões backend/frontend aprovadas.
- **Riscos e dívidas remanescentes:** não há achado Bloqueante ou Importante aberto. Permanecem somente as Sugestões não bloqueantes registradas na revisão e o warning futuro de self-attach do Mockito. Os checks remotos finais das correções foram confirmados pela autora, sem IDs/URLs de execução registrados localmente.

### Evidências por critério

| AC | Status | Teste/comando/evidência |
|---|---|---|
| AC1 | Done | `ExchangeRateIntegrationTest` prova 201, Location, UTC, decimal string e igualdade POST/GET de `effectiveAt`/`createdAt` normalizados para micros. |
| AC2 | Done | Teste preserva versões; porta não expõe update/delete. |
| AC3 | Done | Testes provam futura ignorada e ordenação total. |
| AC4 | Done | Teste prova 404 `EXCHANGE_RATE_NOT_FOUND`. |
| AC5 | Done | Testes HTTP/domínio provam 400 e ausência de escrita; moedas iguais retornam `VALIDATION_ERROR` com `violations[0].field=quoteCurrency` e mensagem determinística. |
| AC6 | Done | Testcontainers PostgreSQL 16 comprova FKs, `NUMERIC(18,8)`, `TIMESTAMPTZ`, checks e ordem/direção do índice; inserções inválidas exercitam constraints; POST com EUR retorna `CURRENCY_NOT_SUPPORTED` sem escrita. |

### File List

| Operação | Arquivo | Motivo |
|---|---|---|
| Criado | `backend/src/main/java/com/srm/creditengine/currency/**` | corte vertical do módulo |
| Criado | `backend/src/main/resources/db/migration/V2__create_exchange_rates.sql` | catálogo e histórico |
| Criado | `backend/src/test/java/com/srm/creditengine/currency/**` | provas unitárias e integradas |
| Alterado | `backend/pom.xml`, `application.yml`, testes baseline | dependências e configuração |
| Alterado | `README.md`, `AI_USAGE.md`, `docs/api/contracts.md`, `docs/database/{ddl.sql,er.md}` | documentação derivada |
| Alterado | `backend/src/main/java/com/srm/creditengine/currency/service/ExchangeRateService.java` | normalização de `effectiveAt` e `createdAt` para micros antes da persistência |
| Criado | `backend/src/main/java/com/srm/creditengine/currency/service/InvalidExchangeRateException.java` | erro de validação com campo determinístico |
| Criado | `backend/src/main/java/com/srm/creditengine/currency/api/ExchangeRateProblemDetail.java`, `ExchangeRateViolation.java` | schemas OpenAPI do erro efetivamente publicado |
| Alterado | `backend/src/main/java/com/srm/creditengine/currency/api/ExchangeRateController.java`, `ExchangeRateExceptionHandler.java` | referências OpenAPI e violação por campo para moedas iguais |
| Alterado | `backend/src/test/java/com/srm/creditengine/currency/ExchangeRateIntegrationTest.java` | precisão temporal, moeda não catalogada, violação por campo, estrutura real da migration e contrato OpenAPI |

### Testes e gates executados

| Data | Comando | Resultado | Evidência |
|---|---|---|---|
| 2026-09-24 | `mvnw.cmd -q spotless:apply`, `spotless:check` e `verify` | aprovado | 25 testes, 0 falhas/erros/ignorados; ArchUnit e PostgreSQL 16/Testcontainers aprovados; JaCoCo 154/160 linhas (96,25%) e 25/32 branches (78,13%) |
| 2026-09-24 | `npm ci`, lint, typecheck, testes e build em instalação limpa | aprovado | 14/14 testes; 100% linhas e 87,5% branches |
| 2026-09-24 | `docker compose up --build -d`, health e validação HTTP | aprovado | PostgreSQL, backend e frontend healthy; moedas iguais: HTTP 400 `application/problem+json`, código, campo e mensagem esperados |
| 2026-09-24 | `/v3/api-docs` | aprovado | POST 400 e GET 400/404 referenciam `ExchangeRateProblemDetail`; campos RFC 9457, `code` e `violations`; item com `field`/`message`; sem `traceId` |
| 2026-09-24 | `check-docs.sh . story` | aprovado com 3 avisos de release | 0 erros |
| 2026-09-24 | `git diff --check` | aprovado | exit 0 |
| 2026-09-24 | GitHub Actions do PR, após as correções finais | aprovado, conforme confirmação da autora | jobs `backend`, `frontend` e `repository` executados novamente e verdes |

### Review Record

- **Revisor/agente:** Codex (GPT-5), revisão final `origin/main...778c3ecfe8c9dd1a7a27596d98b324953079d2a5`, com lentes `blind-hunter`, `edge-case-hunter`, `verification-gap` e `intent-alignment`.
- **Checks remotos:** PR aberto; após a resolução dos três achados Importantes, os jobs `backend`, `frontend` e `repository` foram executados novamente e aprovados no GitHub Actions, conforme confirmação da autora.
- **Achados Bloqueantes:** nenhum.
- **Achados Importantes:** nenhum aberto. Os três achados foram resolvidos e comprovados por testes: violação por campo para moedas iguais; introspecção e exercício das constraints/índice da V2; schema real de erro referenciado nas respostas OpenAPI e documentação sem promessa de `traceId`.
- **Sugestões:** cobrir a fronteira inclusiva `effectiveAt == Clock.instant()`; afirmar todos os campos e o `Location` exato do POST; tratar parâmetros GET ausentes no contrato estável; restringir o advice global e endurecer append-only na superfície Spring Data/banco. A ausência de GET por id torna o `Location` não dereferenciável, mas criar esse endpoint anteciparia escopo e requer decisão posterior.
- **Recomendação:** **Aprovada**. AC1–AC6 estão atendidos, os checks locais e remotos estão verdes e não há achado Bloqueante ou Importante aberto.
- **Aprovação humana:** aprovação final concedida pela autora em 2026-09-24 após confirmar a resolução dos três achados Importantes e a nova execução bem-sucedida dos jobs `backend`, `frontend` e `repository` no GitHub Actions.

### Change Log

| Data | Alteração | Autor/agente |
|---|---|---|
| 2026-09-23 | Story E1-S1 criada em Draft a partir do planejamento, baseline E0 e ADRs aceitos; nenhuma implementação realizada. | Codex |
| 2026-09-23 | Draft refinado com catálogo USD/BRL, convenção UTC, ordenação total e códigos de erro estáveis; nenhuma implementação realizada. | Codex |
| 2026-09-23 | Parâmetro público `at` removido do GET latest; vigência vinculada exclusivamente ao `Clock` do servidor. | Codex |
| 2026-09-23 | Story aprovada humanamente e promovida para `ready-for-dev`; nenhuma implementação realizada. | Autora + Codex |
| 2026-09-24 | E1-S1 implementada; bloqueantes de formatação/OpenAPI, precisão temporal e contradição documental corrigidos; gates integrais aprovados e story movida para Review. | Codex |
| 2026-09-24 | Consistência final: `effectiveAt` normalizado para micros, igualdade temporal POST/GET e rejeição sem escrita de moeda não catalogada comprovadas. | Codex |
| 2026-09-24 | Três achados Importantes resolvidos: violação por campo para moedas iguais, prova estrutural/negativa da migration e schema `ProblemDetail` efetivo no OpenAPI/documentação. | Codex |
| 2026-09-24 | Aprovação humana final registrada após nova execução verde dos jobs remotos; Definition of Done concluída e story promovida de Review para Done. | Autora + Codex |

### Handoff / próximo passo exato

Revisão e aprovação humana final. Após os checks remotos, registrar a decisão sem alterar código ou os blocos congelados.

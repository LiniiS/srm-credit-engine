---
title: 'E1-S2 — Sincronizar câmbio com resiliência'
type: 'feature'
created: '2026-09-24'
status: 'ready-for-dev'
route: 'full'
route_source: 'auto'
review: 'thorough'
review_source: 'pinned'
lenses_ran: []
review_loop_iteration: 0
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/docs/adr/0001-adotar-monolito-modular-hexagonal.md'
  - '{project-root}/docs/adr/0004-padronizar-cambio-e-vigencia.md'
  - '{project-root}/docs/adr/0006-padronizar-contratos-e-erros-http.md'
  - '{project-root}/docs/adr/0007-adotar-observabilidade-e-resiliencia-seletiva.md'
---

# Story E1-S2 — Sincronizar câmbio com resiliência

- **Épico:** E1 — Câmbio auditável
- **Status:** Ready for Dev
- **Prioridade:** Should
- **Predecessoras:** E0-S1, E0-S2 e E1-S1 concluídas

<frozen-after-approval reason="objetivo, critérios, contrato e limites pertencem à autora; mudanças exigem nova aprovação">

## Objetivo e valor

Permitir que o operador sincronize USD/BRL a partir de um provedor FX mockado, determinístico e executável localmente, criando uma nova versão no histórico append-only entregue pela E1-S1. A integração deve falhar de modo controlado, com timeout, retry seletivo e circuit breaker restritos ao adapter externo, sem abrir transação de banco durante a chamada HTTP.

## Requisitos e rastreabilidade

| Fonte | Cobertura |
|---|---|
| E1 / E1-S2; CAP-01 | Sincronização mockada e resiliente de câmbio. |
| RF-02 | Sincronizar taxa por integração mockada. |
| RNF-04 | Validar resposta externa e não expor internals. |
| RNF-07 | Tornar resultado, latência, retries e estado do circuit breaker observáveis. |
| RNF-08 | Timeout, retry seletivo e circuit breaker somente no provider FX. |
| RNF-09 | Provedor mock participa da execução local oficial por Compose. |
| RNF-10 | Testes unitários, de contrato e integração com PostgreSQL 16. |
| RNF-11 | Preservar `api → service → domain ← persistence` e limites ArchUnit. |
| ADR-0001/0002 | Adapter externo no módulo `currency`; JPA/Flyway/PostgreSQL mantidos. |
| ADR-0004 | BASE/QUOTE, append-only, `Instant`/UTC e chamada externa antes da transação. |
| ADR-0006 | `POST /api/v1/exchange-rates/sync`, DTOs, OpenAPI e `ProblemDetail`. |
| ADR-0007 | Resilience4j seletivo, métricas sem alta cardinalidade e nenhum retry global. |

## Contrato da integração

### API pública

`POST /api/v1/exchange-rates/sync`

Request:

```json
{"baseCurrency":"USD","quoteCurrency":"BRL"}
```

- `202 Accepted`, `Location` e o mesmo DTO de taxa persistida da E1-S1 quando provider e persistência concluem com sucesso.
- `400 application/problem+json` com `VALIDATION_ERROR` ou `CURRENCY_NOT_SUPPORTED` para entrada inválida.
- `503 application/problem+json` com código estável `FX_PROVIDER_UNAVAILABLE` quando timeout, falha transitória esgotada, circuit breaker aberto, resposta inválida ou falha externa não recuperável impedirem a sincronização.

### Provider mock HTTP local

`GET /v1/rates?base=USD&quote=BRL`

O provider usa `wiremock/wiremock:3.13.1`, com imagem Docker fixada, e responde `Content-Type: application/json`:

```json
{
  "baseCurrency": "USD",
  "quoteCurrency": "BRL",
  "rate": "5.12345678",
  "effectiveAt": "2026-09-24T12:00:00Z",
  "source": "LOCAL_FX_MOCK"
}
```

USD/BRL é o único par inicial. O mock não exige credencial, inicia no Compose no cenário de sucesso determinístico e usa fixtures/estado administrativo do WireMock para controlar: `503` seguido de sucesso, timeout, `4xx`, `5xx` persistente e payload inválido. Esses controles pertencem ao ambiente de teste/demonstração; o backend chama sempre o contrato normal e não envia parâmetro nem header de controle. URL, timeout e parâmetros de resiliência vêm de variáveis de ambiente; nenhuma resposta é aceita sem validar `Content-Type`, par solicitado, decimal positivo com escala máxima 8, fonte não vazia e instante ISO-8601.

### Ordem de execução e contabilização

1. Validar request e catálogo antes de chamar o provider.
2. Executar circuit breaker externo ao retry, contabilizando uma operação lógica por sincronização.
3. Executar retry internamente, com timeout de 1 segundo em cada tentativa.
4. Validar integralmente o payload após a resposta HTTP.
5. Persistir uma única vez em transação curta, fora da fronteira resiliente.

O circuit breaker ignora `4xx`; payload inválido conta como falha lógica sem retry; falha de persistência não provoca retry e não altera as métricas/estado do breaker.

## Critérios de aceite

### AC1 — Sincronização persiste uma nova versão

**Dado** o mock disponível com uma resposta válida para USD/BRL
**Quando** o endpoint de sync for chamado
**Então** a API retorna `202`, `Location` e a taxa como string decimal
**E** cria exatamente uma nova versão append-only consultável pelo GET latest, preservando `effectiveAt` do provider e gerando `createdAt` pelo `Clock` do servidor.

### AC2 — Chamada externa antecede a transação

**Dado** qualquer tentativa de sincronização
**Quando** o adapter HTTP aguardar ou falhar
**Então** nenhuma transação de banco está aberta durante a chamada externa
**E** somente uma resposta validada alcança a transação curta que reutiliza o registro append-only da E1-S1.

### AC3 — Retry é seletivo e não duplica histórico

**Dado** que o mock falha transitoriamente antes de responder com sucesso
**Quando** a sincronização ocorrer
**Então** timeout, I/O e HTTP `500`, `502`, `503` ou `504` são repetidos até três tentativas totais, com backoff de 100 ms e 200 ms
**E** uma única versão é persistida, ainda que mais de uma chamada HTTP tenha ocorrido
**E** `4xx`, outros `5xx`, payload inválido e falha de persistência não são repetidos.

### AC4 — Timeout e circuit breaker protegem o provider

**Dado** latência acima de 1 segundo por tentativa ou falhas lógicas consecutivas suficientes
**Quando** novas sincronizações forem solicitadas
**Então** a tentativa termina de forma limitada e o circuit breaker count-based abre com janela 4, mínimo 4 e limiar de 50%
**E** permanece aberto por 5 segundos, permite 2 chamadas em half-open e rejeita chamadas sem alcançar o mock enquanto aberto
**E** cada sincronização conta uma operação lógica, independentemente do número de tentativas internas
**E** nenhuma taxa é criada ou alterada.

### AC5 — Falha externa possui contrato seguro e observável

**Dado** timeout, retries esgotados, circuito aberto ou resposta externa inválida
**Quando** a API responder
**Então** retorna `503 application/problem+json` com `FX_PROVIDER_UNAVAILABLE`, sem URL interna, stack trace, classe ou payload bruto do provider
**E** `4xx` não afeta o cálculo do breaker, enquanto payload inválido conta como falha sem retry
**E** falha de persistência permanece fora da fronteira resiliente e não altera o breaker
**E** logs e métricas técnicas permitem distinguir sucesso, erro, retry e estado do circuit breaker sem moeda/id como tag de alta cardinalidade e sem prometer tracing global de E6.

### AC6 — Execução local é determinística e reproduzível

**Dado** checkout limpo sem credenciais externas
**Quando** `docker compose up --build -d` for executado
**Então** PostgreSQL, backend, frontend e provider mock ficam saudáveis
**E** um smoke de sync seguido de GET latest comprova a persistência da taxa fixa versionada no mock.

## Limites de escopo

**Incluído:** porta de saída e adapter HTTP de FX no módulo `currency`; endpoint de sincronização; mock HTTP local no Compose; configuração por ambiente; timeout, retry seletivo, circuit breaker, logs/métricas exclusivamente da integração; reutilização do append-only e dos erros/DTOs da E1-S1; OpenAPI, testes e documentação afetada.

**Excluído:** duplicar ou alterar o POST manual; editar/apagar taxa; fallback que crie nova versão com dado antigo; expiração de 15 minutos para pricing/liquidação; provider real ou credenciais; taxa base/seeds da E1-S3; conversão, pricing, settlement, reporting ou frontend; observabilidade global, tracing, Prometheus/Grafana de E6; autenticação, fila, scheduler, Kafka, Redis ou microserviço produtivo.

## Matriz de comportamento e bordas

| Cenário | Estado/entrada | Resultado |
|---|---|---|
| Sucesso | USD/BRL e payload válido | 202; uma versão persistida. |
| Transitório recuperado | 503 e depois sucesso | até 3 tentativas, backoff 100/200 ms; uma escrita e uma operação lógica no breaker. |
| HTTP 4xx | resposta permanente do provider | sem retry; ignorado pelo breaker; 503 público; zero escrita. |
| 5xx elegível persistente | 500/502/503/504 | 3 tentativas; uma falha lógica no breaker; 503; zero escrita. |
| Outro 5xx | código fora da allowlist | sem retry; uma falha lógica no breaker; 503; zero escrita. |
| Timeout | mais de 1 s em cada tentativa | até 3 tentativas; uma falha lógica no breaker; 503; zero escrita. |
| Circuito aberto | janela 4, mínimo 4, falhas ≥ 50% | 5 s fail-fast; depois 2 chamadas half-open; zero escrita durante rejeição. |
| Payload inválido | Content-Type/par/decimal/tempo/fonte inválido | sem retry; conta falha no breaker; 503 seguro; zero escrita. |
| Persistência falha | provider respondeu, banco rejeita | fora do breaker/retry; sem nova chamada HTTP e sem registro parcial. |
| Moeda inválida/não catalogada | request inválido | 400 antes do provider; zero chamada e zero escrita. |

</frozen-after-approval>

## Tarefas técnicas ordenadas

- [ ] **T1 — Contratos e configuração (AC1–AC6):** definir request/result, porta `ExchangeRateProvider`, resposta tipada e propriedades validadas; preservar tipos decimais/temporais e configuração por ambiente.
- [ ] **T2 — Mock local (AC3–AC6):** adicionar `wiremock/wiremock:3.13.1` ao Compose, saudável e inicialmente em sucesso; versionar fixtures/estado administrativo para 503→sucesso, timeout, 4xx, 5xx persistente e payload inválido; garantir que o backend não envie controles de teste.
- [ ] **T3 — Adapter resiliente (AC2–AC5):** implementar cliente HTTP no `currency.persistence`/adapter de saída com ordem `circuit breaker → retry → timeout por tentativa`; configurar 1 s, 3 tentativas, backoff 100/200 ms e allowlist 500/502/503/504, timeout/I/O; excluir 4xx, demais 5xx e payload inválido do retry.
- [ ] **T4 — Caso de uso e transação (AC1–AC5):** validar entrada/catálogo antes do provider, validar payload após HTTP e persistir uma única vez em fronteira transacional curta fora do breaker/retry; contabilizar uma operação lógica, ignorar 4xx no breaker, contar payload inválido e excluir persistência.
- [ ] **T5 — API e erro (AC1, AC5):** expor POST sync, `202`/`Location`, OpenAPI e `FX_PROVIDER_UNAVAILABLE` no advice existente, sem internals.
- [ ] **T6 — Observabilidade seletiva (AC3–AC5):** registrar eventos técnicos seguros e métricas de chamada/retry/circuit breaker sem alta cardinalidade; não antecipar tracing/stack global de E6.
- [ ] **T7 — Provas e documentação (AC1–AC6):** testar provider, resiliência, ausência de transação externa, zero escrita nas falhas, uma escrita após retry, Compose/smoke e regressões; atualizar README, contratos e AI_USAGE somente com fatos reais.

## Code Map e arquivos previstos

| Caminho | Ação prevista |
|---|---|
| `backend/pom.xml` | Dependências mínimas de Resilience4j e teste HTTP; sem stack global de observabilidade. |
| `backend/src/main/resources/application.yml` | Propriedades `srm.fx-provider.*` e instância Resilience4j por ambiente. |
| `backend/src/main/java/com/srm/creditengine/currency/domain/port/**` | Porta do provider, sem Spring/Jackson/JPA. |
| `backend/src/main/java/com/srm/creditengine/currency/service/**` | Caso de uso sem transação durante I/O; persistência delegada a fronteira transacional curta. |
| `backend/src/main/java/com/srm/creditengine/currency/persistence/**` | Adapter HTTP externo e mapeamento validado; reutilizar adapter JPA existente. |
| `backend/src/main/java/com/srm/creditengine/currency/api/**` | Endpoint sync, DTO e erro 503/OpenAPI. |
| `backend/src/test/java/com/srm/creditengine/currency/**` | Contrato HTTP, resiliência determinística, transação e PostgreSQL/Testcontainers. |
| `infra/fx-mock/**`, `compose.yaml`, `.env.example` | Mock versionado, healthcheck e configuração local. |
| `docs/api/contracts.md`, `README.md`, `AI_USAGE.md`, esta story | Contrato, operação e evidências reais. |

Não alterar V1/V2, schema/DDL/ER, frontend ou regras ArchUnit. A implementação deve reutilizar `ExchangeRate`, `ExchangeRateRepository`, normalização para micros, DTO de resposta e tratamento RFC 9457 existentes.

## Estratégia de testes e gates

- **Unidade/contrato do adapter:** WireMock controlado; afirmar GET, query, `application/json`, JSON aprovado, único par inicial e ausência de header/query de teste enviados pelo backend.
- **Resiliência:** provar 3 tentativas e backoff 100/200 ms para timeout/I/O/500/502/503/504; uma tentativa para 4xx, demais 5xx e payload inválido; tempo virtual/controlado, sem espera real; provar janela 4, limiar 50%, 5 s open e 2 chamadas half-open.
- **Ordem/contabilização:** instrumentar decorators para provar `circuit breaker → retry → timeout`; retry gera uma operação no breaker; 4xx ignorado; payload inválido contabilizado; persistência invisível à resiliência.
- **Transação/persistência:** instrumentation/test double prova ausência de transação durante HTTP; Testcontainers prova uma escrita após 503→sucesso e zero escrita nas falhas; erro de banco não repete HTTP nem afeta breaker.
- **HTTP/OpenAPI:** 202/Location e 400/503 `ProblemDetail`; schema e códigos estáveis; sem internals.
- **Integração local:** Compose healthy; sync real contra mock; GET latest retorna id/valor sincronizado; nenhum segredo necessário.
- **Regressão:** E1-S1, ArchUnit, JaCoCo e frontend permanecem verdes.

```bash
cd backend && ./mvnw -q spotless:check && ./mvnw -q verify
cd frontend && npm ci && npm run lint && npm run typecheck && npm run test -- --run && npm run build
docker compose config
docker compose up --build -d
docker compose ps
curl -i -X POST http://localhost:8080/api/v1/exchange-rates/sync ...
curl -i 'http://localhost:8080/api/v1/exchange-rates/latest?base=USD&quote=BRL'
bash .agents/skills/srm-documentacao/scripts/check-docs.sh . story
git diff --check
docker compose down
```

## Riscos, dependências e pré-condições

- **Atendidas:** E0-S1/E0-S2/E1-S1 `Done`; catálogo USD/BRL, append-only, Clock e CI disponíveis; ADRs 0001–0009 aceitos.
- **Transação acidentalmente ampla:** `@Transactional` no orquestrador envolveria HTTP; separar explicitamente fetch e escrita e provar com teste.
- **Retry indevido:** a allowlist é fechada em timeout, I/O, 500, 502, 503 e 504; 4xx, demais 5xx, payload inválido e persistência não repetem.
- **Contabilização inflada:** circuit breaker externo ao retry deve observar uma operação lógica; 4xx é ignorado, payload inválido conta e persistência fica fora.
- **Duplicação:** retry envolve somente GET idempotente do provider, nunca persistência; uma única chamada ao append após sucesso.
- **Testes lentos/flaky:** sobrescrever durações/janela em teste e controlar servidor/eventos; não aguardar parâmetros produtivos reais.
- **Semântica 202:** o planejamento fixa `202`, embora a persistência já esteja concluída na resposta; qualquer mudança para 201 exige aprovação de contrato.

## Decisões humanas aprovadas

1. **Mock:** WireMock `3.13.1`, contrato JSON/`application/json` acima, USD/BRL único par inicial, sucesso como estado inicial e falhas controladas apenas por fixtures/API administrativa.
2. **Resiliência:** timeout de 1 s por tentativa; 3 tentativas; backoff 100/200 ms; retry somente para timeout, I/O, 500, 502, 503 e 504; circuit breaker count-based 4/4/50%, aberto 5 s e 2 chamadas half-open; tudo configurável por ambiente e reduzido nos testes.
3. **Ordem:** validação de entrada → breaker → retry → timeout → validação do payload → persistência transacional curta; uma operação lógica no breaker, 4xx ignorado, payload inválido contabilizado sem retry e persistência fora da resiliência.

## Definition of Ready

- [x] Objetivo único, predecessoras, rastreabilidade e limites definidos.
- [x] ACs Given/When/Then cobrem sucesso, falhas e integridade append-only.
- [x] Fronteira transacional, segurança e estratégia de testes estão explícitas.
- [x] Provider é local, determinístico e não exige credenciais.
- [x] Escopo exclui E1-S3, conversão, pricing, frontend e observabilidade global.
- [x] Contrato do mock, política de resiliência e ordem de execução aprovados humanamente.

**Resultado da DoR:** integralmente atendida; decisões aprovadas humanamente em 2026-09-24 e story promovida para `ready-for-dev`.

## Definition of Done

- [ ] AC1–AC6 atendidos com evidências automatizadas e smoke real.
- [ ] Timeout, retry e circuit breaker ficam somente no adapter FX e são comprovados sem testes instáveis.
- [ ] Ordem `breaker → retry → timeout`, uma operação lógica, 4xx ignorado, payload inválido contabilizado e persistência fora da resiliência são comprovados.
- [ ] Nenhuma chamada externa ocorre em transação aberta; falhas geram zero escrita e retry recuperado gera uma escrita.
- [ ] Provider mock/Compose funcionam sem credencial e todos os serviços ficam healthy.
- [ ] `spotless:check`, `verify`, ArchUnit, JaCoCo e regressão frontend passam.
- [ ] OpenAPI/ProblemDetail, README, contratos, AI_USAGE e story refletem fatos reais.
- [ ] Revisão não deixa achado Bloqueante/Importante aberto; aprovação humana final registrada.
- [ ] Plano de commits preparado; Git mutável reservado à autora.

## Campos BMAD para implementação, revisão e evidências

### Dev Agent Record

- **Agente/modelo:**
- **Branch/baseline observada:**
- **Plano de implementação:** T1 → T7
- **Decisões locais / desvios:**
- **Completion Notes:**
- **Riscos e dívidas remanescentes:**

### Evidências por critério

| AC | Status | Teste/comando/evidência |
|---|---|---|
| AC1 | Pending | |
| AC2 | Pending | |
| AC3 | Pending | |
| AC4 | Pending | |
| AC5 | Pending | |
| AC6 | Pending | |

### File List

| Operação | Arquivo | Motivo |
|---|---|---|

### Testes e gates executados

| Data | Comando | Resultado | Evidência |
|---|---|---|---|

### Review Record

- **Revisor/agente:** Codex (GPT-5), revisão documental pré-implementação.
- **Checks remotos:** não aplicável nesta etapa documental.
- **Achados Bloqueantes:** nenhum; decisões antes pendentes foram resolvidas pela autora.
- **Achados Importantes:** nenhum aberto no artefato aprovado.
- **Sugestões:** nenhuma registrada nesta aprovação.
- **Recomendação:** aprovada para desenvolvimento, mantendo implementação e evidências pendentes.
- **Aprovação humana:** contrato do mock, política de resiliência e ordem de execução aprovados pela autora em 2026-09-24; story pronta para desenvolvimento.

### Change Log

| Data | Alteração | Autor/agente |
|---|---|---|
| 2026-09-24 | Story E1-S2 criada em Draft a partir do planejamento, ADRs aceitos e baseline E1-S1; nenhuma implementação realizada. | Codex |
| 2026-09-24 | WireMock, contrato, cenários, política de resiliência e ordem de execução aprovados humanamente; DoR concluída e story promovida para Ready for Dev. | Autora + Codex |

### Handoff / próximo passo exato

Iniciar implementação em branch curta da E1-S2, respeitando T1–T7 e o bloco congelado. Nenhuma implementação foi realizada nesta etapa.

## Implementation Notes

## Spec Change Log

## Review Triage Log

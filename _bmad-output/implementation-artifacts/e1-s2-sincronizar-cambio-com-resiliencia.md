---
title: 'E1-S2 — Sincronizar câmbio com resiliência'
type: 'feature'
created: '2026-09-24'
status: 'review'
baseline_commit: '14184f47e1176cf3233be8b5046ee96a24897c22'
route: 'full'
route_source: 'auto'
review: 'thorough'
review_source: 'auto'
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap']
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
- **Status:** Review
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

- [x] **T1 — Contratos e configuração (AC1–AC6):** definir request/result, porta `ExchangeRateProvider`, resposta tipada e propriedades validadas; preservar tipos decimais/temporais e configuração por ambiente.
- [x] **T2 — Mock local (AC3–AC6):** adicionar `wiremock/wiremock:3.13.1` ao Compose, saudável e inicialmente em sucesso; versionar fixtures/estado administrativo para 503→sucesso, timeout, 4xx, 5xx persistente e payload inválido; garantir que o backend não envie controles de teste.
- [x] **T3 — Adapter resiliente (AC2–AC5):** implementar cliente HTTP no `currency.persistence`/adapter de saída com ordem `circuit breaker → retry → timeout por tentativa`; configurar 1 s, 3 tentativas, backoff 100/200 ms e allowlist 500/502/503/504, timeout/I/O; excluir 4xx, demais 5xx e payload inválido do retry.
- [x] **T4 — Caso de uso e transação (AC1–AC5):** validar entrada/catálogo antes do provider, validar payload após HTTP e persistir uma única vez em fronteira transacional curta fora do breaker/retry; contabilizar uma operação lógica, ignorar 4xx no breaker, contar payload inválido e excluir persistência.
- [x] **T5 — API e erro (AC1, AC5):** expor POST sync, `202`/`Location`, OpenAPI e `FX_PROVIDER_UNAVAILABLE` no advice existente, sem internals.
- [x] **T6 — Observabilidade seletiva (AC3–AC5):** registrar eventos técnicos seguros e métricas de chamada/retry/circuit breaker sem alta cardinalidade; não antecipar tracing/stack global de E6.
- [x] **T7 — Provas e documentação (AC1–AC6):** testar provider, resiliência, ausência de transação externa, zero escrita nas falhas, uma escrita após retry, Compose/smoke e regressões; atualizar README, contratos e AI_USAGE somente com fatos reais.

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

- [x] AC1–AC6 atendidos com evidências automatizadas e smoke real.
- [x] Timeout, retry e circuit breaker ficam somente no adapter FX e são comprovados sem testes instáveis.
- [x] Ordem `breaker → retry → timeout`, uma operação lógica, 4xx ignorado, payload inválido contabilizado e persistência fora da resiliência são comprovados.
- [x] Nenhuma chamada externa ocorre em transação aberta; falhas geram zero escrita e retry recuperado gera uma escrita.
- [x] Provider mock/Compose funcionam sem credencial e todos os serviços ficam healthy.
- [x] `spotless:check`, `verify`, ArchUnit, JaCoCo e regressão frontend passam.
- [x] OpenAPI/ProblemDetail, README, contratos, AI_USAGE e story refletem fatos reais.
- [ ] Revisão não deixa achado Bloqueante/Importante aberto; aprovação humana final registrada.
- [x] Plano de commits preparado; Git mutável reservado à autora.

## Campos BMAD para implementação, revisão e evidências

### Dev Agent Record

- **Agente/modelo:** Codex (GPT-5), com implementação inicial delegada pelo workflow BMAD e conclusão/revisão pelo agente principal.
- **Branch/baseline observada:** `feature/e1-s2-resilient-fx-sync` / `14184f47e1176cf3233be8b5046ee96a24897c22`.
- **Plano de implementação:** T1 → T7
- **Decisões locais / desvios:** `RestClient` com cliente JDK; decorators Resilience4j programáticos para tornar explícita a ordem; corpo do provider limitado a 16 KiB; fixtures de falha instaladas uma por vez pela API administrativa do WireMock; métricas Micrometer expostas no Actuator local, sem Prometheus/tracing.
- **Completion Notes:** T1–T7 concluídas. O adapter valida contrato completo, limita payload, aplica timeout/retry/breaker configuráveis, persiste fora da fronteira resiliente e publica contrato seguro. Auto-revisão corrigiu propagação das variáveis no Compose, observabilidade consultável, validação de propriedades, limite de corpo e lacunas de teste. Smoke final isolado no projeto `srm_e1s2_smoke`, com PostgreSQL vazio, comprovou igualdade exata entre POST sync e GET latest. A correção final configurou `Redirect.NEVER`, restringiu sucesso do provider a HTTP `200` e comprovou que `302`/`204` não sofrem retry, não persistem e contam como falha lógica.
- **Riscos e dívidas remanescentes:** nenhum achado Bloqueante ou Importante permanece aberto localmente. Os checks remotos anteriormente aprovados precisam ser executados novamente após commit/push humano desta correção. As sugestões da revisão permanecem deliberadamente não implementadas.

### Evidências por critério

| AC | Status | Teste/comando/evidência |
|---|---|---|
| AC1 | Atendido | HTTP/Testcontainers: sync `202`, `Location`, DTO decimal e exatamente uma linha; integração valida append-only. |
| AC2 | Atendido | `ExchangeRateTransactionBoundaryTest`: provider sem transação e append em transação Spring ativa. |
| AC3 | Atendido | Testes 500/502/503/504, I/O, 4xx, outro 5xx, `302`, `204` e payload; smoke WireMock 503→sucesso mostrou 2 chamadas e delta de 1 linha. |
| AC4 | Atendido | Testes de timeout por tentativa, janela 4/4/50%, open 5 s, duas permissões half-open, rejeição fail-fast e contabilização lógica de `302` sem seguir o redirect. |
| AC5 | Atendido | Teste HTTP e smoke do `503 FX_PROVIDER_UNAVAILABLE`; `302` faz exatamente uma chamada, não alcança o destino, não escreve e conta falha no breaker; `204` também é inválido sem retry. |
| AC6 | Atendido | Projeto isolado `srm_e1s2_smoke`: quatro serviços `healthy`; banco iniciou com 0 linhas; POST `202` e GET `200` retornaram exatamente id `8a2ff7e2-393d-49ca-bf9e-0821ada8381e`, rate `5.12345678`, source `LOCAL_FX_MOCK` e effectiveAt `2026-09-24T12:00:00Z`; banco terminou com exatamente 1 linha. |

### File List

| Operação | Arquivo | Motivo |
|---|---|---|
| Modificado | `.env.example`, `compose.yaml` | Configuração local e quarto serviço WireMock. |
| Modificado | `backend/pom.xml`, `backend/src/main/resources/application.yml` | Resilience4j e propriedades/Actuator. |
| Modificado | `backend/src/main/java/com/srm/creditengine/CreditEngineApplication.java` | Scan das propriedades tipadas. |
| Modificado | `backend/src/main/java/com/srm/creditengine/currency/api/ExchangeRateController.java` | Endpoint público de sincronização. |
| Modificado | `backend/src/main/java/com/srm/creditengine/currency/api/ExchangeRateExceptionHandler.java` | Erro estável `FX_PROVIDER_UNAVAILABLE`. |
| Criado | `backend/src/main/java/com/srm/creditengine/currency/api/ExchangeRateSyncRequest.java` | Request validado do sync. |
| Criado | `backend/src/main/java/com/srm/creditengine/currency/domain/port/ExchangeRateProvider.java` | Porta de saída do provider. |
| Criado | `backend/src/main/java/com/srm/creditengine/currency/domain/port/ExchangeRateProviderException.java` | Falha neutra da porta. |
| Criado | `backend/src/main/java/com/srm/creditengine/currency/domain/port/ProvidedExchangeRate.java` | Resposta tipada neutra. |
| Criado | `backend/src/main/java/com/srm/creditengine/currency/persistence/FxProviderProperties.java` | Configuração validada por ambiente. |
| Criado | `backend/src/main/java/com/srm/creditengine/currency/persistence/HttpExchangeRateProvider.java` | Adapter HTTP, validação, resiliência e métricas. |
| Modificado | `backend/src/main/java/com/srm/creditengine/currency/service/ExchangeRateService.java` | Orquestração sem transação externa. |
| Criado | `backend/src/main/java/com/srm/creditengine/currency/service/ExchangeRateWriter.java` | Fronteira transacional curta. |
| Criado | `backend/src/main/java/com/srm/creditengine/currency/service/FxProviderUnavailableException.java` | Erro do caso de uso. |
| Modificado | `backend/src/test/java/com/srm/creditengine/currency/ExchangeRateIntegrationTest.java` | Sync, validação e OpenAPI com PostgreSQL. |
| Criado | `backend/src/test/java/com/srm/creditengine/currency/api/ExchangeRateSyncApiTest.java` | ProblemDetail 503 seguro. |
| Criado | `backend/src/test/java/com/srm/creditengine/currency/persistence/FxProviderPropertiesTest.java` | Propriedades inválidas. |
| Criado | `backend/src/test/java/com/srm/creditengine/currency/persistence/HttpExchangeRateProviderTest.java` | Contrato, matriz de retry, payload, timeout, breaker e métricas. |
| Criado | `backend/src/test/java/com/srm/creditengine/currency/service/ExchangeRateServiceSyncTest.java` | Ordem, uma escrita e falha de persistência. |
| Criado | `backend/src/test/java/com/srm/creditengine/currency/service/ExchangeRateTransactionBoundaryTest.java` | Prova Spring da fronteira transacional. |
| Criado | `infra/fx-mock/mappings/usd-brl-success.json` | Sucesso determinístico inicial. |
| Criado | `infra/fx-mock/scenarios/*.json` | Fixtures administrativas de 503→sucesso, timeout, 4xx, 503 persistente e payload inválido. |
| Modificado | `README.md`, `docs/api/contracts.md`, `docs/architecture/c4-container.md`, `AI_USAGE.md` | Operação, contrato, arquitetura e uso material de IA. |
| Modificado | `_bmad-output/implementation-artifacts/e1-s2-sincronizar-cambio-com-resiliencia.md` | Estado, evidências e revisão da story. |

### Testes e gates executados

| Data | Comando | Resultado | Evidência |
|---|---|---|---|
| 2026-09-24 | `mvn ... spotless:check` | Passou | Java formatado; nenhum desvio. |
| 2026-09-24 | `mvn ... verify` | Passou | 57 testes, 0 falhas/erros/skips; ArchUnit 13 testes e PostgreSQL 16/Testcontainers verdes; JaCoCo linhas 98,23% (333/339). |
| 2026-09-24 | `npm ci`, lint, typecheck, test, build | Passou | Cópia temporária limpa: 14 testes; 100% statements/lines, 87,5% branches; build Vite verde. |
| 2026-09-24 | `docker compose config` | Passou | Compose coerente com quatro serviços e variáveis de resiliência. |
| 2026-09-24 | `docker compose up --build -d`, `ps` | Passou | PostgreSQL, backend, frontend e WireMock `healthy`. |
| 2026-09-24 | Smoke POST/GET/OpenAPI/Swagger/métricas | Passou | Sync `202`; readiness/frontend/OpenAPI/Swagger `200`; responses 202/400/503; métricas FX consultáveis. |
| 2026-09-24 | Smoke administrativo 503→sucesso | Passou | 2 chamadas ao provider e delta de exatamente 1 linha. |
| 2026-09-24 | Payload inválido | Passou | `503`, zero escrita; teste HTTP confirma corpo `FX_PROVIDER_UNAVAILABLE` sem internals. |
| 2026-09-24 | `docker compose down` | Passou | Ambiente encerrado sem `-v`; volumes preservados. |
| 2026-09-24 | Smoke isolado `docker compose -p srm_e1s2_smoke` | Passou | Volume exclusivo `srm_e1s2_smoke_postgres_data`; 4 serviços healthy; 0→1 linha; POST/GET id, rate, source e effectiveAt idênticos; projeto e volume temporários removidos. `srm_postgres_data` permaneceu existente com `CreatedAt=2026-09-23T16:17:37Z` e o mesmo mountpoint. |
| 2026-09-24 | Regressão frontend em cópia temporária limpa | Passou | `npm ci`, lint, typecheck, 14 testes e build; 100% statements/lines/functions e 87,5% branches. O workspace tinha um binário nativo travado pelo editor, sem impacto nos arquivos versionados. |
| 2026-09-24 | Smoke final `302` e sucesso após rebuild | Passou | `302` retornou `503 FX_PROVIDER_UNAVAILABLE`; delta de 1 chamada em `/v1/rates`, 0 no destino e 0 no banco. Após remover somente os mappings temporários, sucesso retornou `202` e delta de 1 linha. OpenAPI `202/400/503` e Swagger `200`. |

### Review Record

- **Revisor/agente:** Codex (GPT-5) com lentes BMAD `blind-hunter`, `edge-case-hunter` e `verification-gap`; lente de intent omitida por ausência de seção `## Intent` na story.
- **Base da revisão final:** branch `feature/e1-s2-resilient-fx-sync`, HEAD `3b82c76`, comparada com `origin/main` em `5af67da0a2311795840baf487b6e186862c26374`; cinco commits e 33 arquivos alterados, sem mudanças locais pendentes no início da revisão.
- **Checks remotos:** PR aberto; jobs reais `backend`, `frontend` e `repository` aprovados novamente no GitHub Actions, conforme confirmação humana da autora. Gates locais e smoke isolado também confirmados como aprovados.
- **Achados Bloqueantes:** nenhum.
- **Achados Importantes:** nenhum aberto. O aceite indevido de `3xx` foi resolvido com `HttpClient.Redirect.NEVER`, sucesso exclusivo para HTTP `200` e provas de `302`/`204`, zero retry, zero escrita, erro seguro e falha lógica no breaker.
- **Sugestões:** automatizar no CI o smoke Compose atualmente manual; tornar determinísticas e mais completas as provas de timeout, transição half-open e limiar de 50%; afirmar métricas de falha/duração e binding de todas as propriedades inválidas; validar `initialBackoff >= 1 ms`; documentar uma semântica estável para o gauge de estado e alinhar o C4 futuro de Prometheus com o runtime atual. Nenhuma dessas sugestões substitui o smoke e os gates já aprovados.
- **Limitações remanescentes:** o fluxo Compose real foi comprovado por smoke isolado, mas ainda não integra a verificação automatizada do CI; as transições temporais do Resilience4j são verificadas principalmente por configuração e comportamento da biblioteca, não por relógio virtual do projeto. São sugestões, não achados Importantes.
- **Recomendação:** correção local **Aprovada**; reexecutar os checks remotos após commit/push humano e manter a story em `Review` até a aprovação final da autora.
- **Aprovação humana:** aprovação final pendente; somente as decisões de contrato/resiliência e a promoção anterior para desenvolvimento foram humanas.

### Change Log

| Data | Alteração | Autor/agente |
|---|---|---|
| 2026-09-24 | Story E1-S2 criada em Draft a partir do planejamento, ADRs aceitos e baseline E1-S1; nenhuma implementação realizada. | Codex |
| 2026-09-24 | WireMock, contrato, cenários, política de resiliência e ordem de execução aprovados humanamente; DoR concluída e story promovida para Ready for Dev. | Autora + Codex |
| 2026-09-24 | E1-S2 implementada, validada localmente e movida para Review; auto-revisão corrigiu os achados materiais sem alterar o bloco congelado. | Codex |
| 2026-09-24 | Evidência final AC6 executada em Compose isolado com PostgreSQL vazio; POST sync e GET latest idênticos e uma única linha persistida. | Codex |
| 2026-09-24 | Achado Importante final corrigido: redirects desabilitados, somente HTTP 200 aceito e `302`/`204` comprovados como falhas sem retry ou escrita; gates locais e smokes finais aprovados. | Codex |

### Handoff / próximo passo exato

Revisar o diff, executar os checks no PR e registrar aprovação humana antes de mover a story para Done.

## Implementation Notes

## Spec Change Log

## Review Triage Log

| ID | Veredito/rota | Evidência |
|---|---|---|
| BH-01 | `false` / rejeitado | `scenarios/` é deliberadamente um catálogo administrativo; o smoke publicou a fixture via `POST /__admin/mappings` e obteve 503→sucesso. |
| BH-02 | `low` / corrigido em documentação | Fixtures são instaladas uma por vez; README passou a explicitar seleção única e reset, eliminando competição. |
| BH-03 | `medium` / patch resolvido | Compose agora propaga timeout, retry, backoff e todos os parâmetros do breaker. |
| BH-04 | `medium` / patch resolvido | Endpoint local `/actuator/metrics` foi exposto e smoke confirmou `srm.fx.provider.calls`. |
| BH-05 | `medium` / patch resolvido | Leitura externa limitada a 16 KiB e teste prova rejeição sem retry. |
| BH-06 | `medium` / patch resolvido | Durações positivas e relação mínimo≤janela são validadas; teste dedicado cobre falhas. |
| BH-07 | `medium` / patch resolvido | Matriz parametrizada comprova que payload inválido gera uma falha lógica e zero retry. |
| BH-08 | `false` / rejeitado | O teste verifica configuração de 5 s e duas permissões half-open; transição temporal automática pertence ao Resilience4j, não ao código do projeto. |
| BH-09 | `low` / rejeitado | A persistência PostgreSQL e a resiliência são provadas separadamente; smoke real 503→sucesso comprovou integração com delta de uma linha. |
| BH-10 | `false` / rejeitado | As fixtures foram exercitadas manualmente no Compose final; a exigência da story não fixa sua automação no CI. |
| BH-11 | `low` / patch resolvido | Tabela de contratos agora registra `202 + Location` e erros 400/503. |
| BH-12 | `false` / rejeitado | Era estado transitório esperado antes do fechamento; tasks, ACs, evidências e status foram atualizados ao final. |
| EC-01 | `medium` / patch resolvido | Mesmo achado BH-05; limite de 16 KiB aplicado antes do parse. |
| EC-02 | `medium` / patch resolvido | Mesmo achado BH-06; configuração inválida falha cedo com mensagem estável. |
| EC-03 | `false` / rejeitado | Seleção administrativa foi comprovada no WireMock real e documentada no README. |
| VG-01 | `low` / rejeitado | Smoke Compose real cobriu wiring backend→WireMock→PostgreSQL; script CI seria melhoria fora do gate aprovado. |
| VG-02 | `medium` / patch resolvido | Testes parametrizados cobrem Content-Type, par, sinal, escala, precisão, instante, fonte e tamanho. |
| VG-03 | `medium` / patch resolvido | Teste HTTP de sync cobre moeda malformada, não catalogada e par igual, com zero escrita; unidade prova provider não chamado. |
| VG-04 | `medium` / patch resolvido | Teste Spring gerenciado prova provider fora de transação e append dentro da transação curta. |
| FR-BH-01 | `false` / rejeitado | A URL interna do Compose é deliberadamente `http://fx-mock:8080`; README e `.env.example` qualificam `FX_PROVIDER_URL` como configuração para execução fora do Compose. |
| FR-BH-02 | `low` / sugestão | A métrica confirma dois retries de timeout, mas o teste aceita entre uma e três chegadas ao servidor; uma prova determinística das três tentativas reduziria falso verde. |
| FR-BH-03 | `low` / sugestão | O teste de timeout usa espera limitada real; hoje passa, mas relógio/cliente controlado seria menos sujeito a variação de CI. |
| FR-BH-04 | `low` / sugestão | A configuração e as duas permissões half-open são provadas, mas a transição temporal automática é delegada à biblioteca. |
| FR-BH-05 | `false` / rejeitado | O limiar de 50% é afirmado diretamente na configuração construída; testar novamente o algoritmo interno do Resilience4j não é requisito do adapter. |
| FR-BH-06 | `low` / sugestão | O caminho de I/O por conexão recusada é coberto; uma falha durante leitura do body fortaleceria a prova de encapsulamento do `RestClient`. |
| FR-BH-07 | `false` / rejeitado | A persistência ocorre somente depois que a chamada decorada termina; portanto sua exceção está estruturalmente fora do retry e do breaker. |
| FR-BH-08 | `low` / sugestão | HTTP real, PostgreSQL real e retry→escrita foram comprovados entre testes e smoke; reuni-los em um teste Testcontainers automatizado melhoraria a proteção contra regressão. |
| FR-BH-09 | `low` / sugestão | As fixtures foram exercitadas no smoke, mas ainda não são validadas por gate automatizado. |
| FR-BH-10 | `low` / sugestão | O README descreve a API administrativa, mas um exemplo completo de `curl` para publicar fixture reduziria erro operacional. |
| FR-BH-11 | `low` / sugestão | A validação existe, mas o teste dedicado não percorre todas as anotações nem a duração de open. |
| FR-BH-12 | `low` / rejeitado | Limites superiores não foram definidos pela decisão aprovada; inventá-los nesta story mudaria a política configurável sem base normativa. |
| FR-BH-13 | `low` / sugestão | O gauge por ordinal é de baixa cardinalidade, mas sua semântica numérica depende da enum da biblioteca e merece contrato estável/documentado. |
| FR-BH-14 | `false` / rejeitado | A exposição de `metrics` é intencional para a evidência local da story; autenticação e hardening de deployment estão explicitamente fora do escopo. |
| FR-BH-15 | `low` / sugestão | O C4 ainda mostra o alvo futuro `/actuator/prometheus`, enquanto a nota textual diz que a observação global foi adiada; convém distinguir estado atual e alvo. |
| FR-EC-01 | `medium` / patch resolvido | `HttpClient.Redirect.NEVER` impede seguir redirects e o adapter aceita somente HTTP `200`; testes e smoke provam `302` com uma chamada, destino não alcançado, zero retry/escrita, `503` seguro e uma falha lógica. `204` também é rejeitado. |
| FR-EC-02 | `low` / sugestão | `initialBackoff` positivo, mas inferior a 1 ms, vira `0` em `toMillis()` e é rejeitado pelo Resilience4j; a validação pode antecipar esse erro. |
| FR-VG-01 | `low` / sugestão | O smoke real está evidenciado e aprovado, mas não faz parte do workflow repetível do CI. |
| FR-VG-02 | `low` / sugestão | Os ramos de falha executam as métricas, porém faltam asserts específicos para cada counter/timer de resultado. |
| FR-VG-03 | `low` / sugestão | Um teste de binding do contexto para todas as combinações inválidas protegeria melhor as constraints de configuração. |

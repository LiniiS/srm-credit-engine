---
title: 'E2-S3 — Simular cross-currency'
type: 'feature'
created: '2026-09-26'
status: 'in-review'
baseline_commit: 'e58c3cf3643b2dec9745cbeb153dca43efc637f4'
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
  - 'docs/adr/0001-adotar-monolito-modular-hexagonal.md'
  - 'docs/adr/0003-padronizar-calculo-financeiro-decimal.md'
  - 'docs/adr/0004-padronizar-cambio-e-vigencia.md'
  - 'docs/adr/0006-padronizar-contratos-e-erros-http.md'
---

# E2-S3 — Simular cross-currency

**Status:** Ready for Dev

<frozen-after-approval reason="intenção, critérios e decisões pertencem à responsável humana">

## Objetivo e valor

Permitir que a simulação entregue pela E2-S2 calcule primeiro o valor presente na moeda do título e, quando a moeda de pagamento divergir, converta o valor presente não arredondado usando uma versão cambial persistida, vigente e não expirada. A simulação continua sem persistência, mas retorna o snapshot cambial aplicado para rastreabilidade e preparação da futura liquidação.

## Escopo

- Evoluir `POST /api/v1/pricing/simulations` de modo retrocompatível: o campo JSON existente `currency` continua sendo a moeda do título, e `paymentCurrencyCode` é opcional com default igual a `currency`.
- Preservar todos os campos existentes da resposta e adicionar `paymentCurrencyCode`, `presentValueInPaymentCurrency` e `exchangeRate` nullable.
- Criar no módulo `currency` uma porta pública mínima de consulta cambial aplicável, consumida por `pricing` sem acessar JPA ou services internos.
- Aplicar a convenção BASE/QUOTE do ADR-0004: USD/BRL significa quantos BRL equivalem a 1 USD; USD→BRL multiplica e BRL→USD divide.
- Consultar a versão vigente no `Clock.instant()` e validar sua idade desde `effectiveAt` com janela configurável de 15 minutos.
- Mesma moeda não usa FX; toda simulação permanece read-only e sem snapshot persistido.

## Fora de escopo

- Renomear/remover o campo legado `currency`, versionar a API ou quebrar payloads da E2-S2.
- Liquidação, recebível, lote, idempotência, concorrência, snapshot persistido ou nova tabela/migration.
- Sincronização automática, fallback externo, novo provider, novos pares/moedas ou cotação inversa fictícia.
- Alterar taxa-base, Strategy, calendário, fórmula E2-S2 ou persistir simulações.
- Frontend funcional, settlement, reporting/jOOQ e qualquer funcionalidade de E3+.

## Contrato REST aprovado

### Requisição

- Preserva integralmente a requisição E2-S2.
- `currency` continua obrigatório e representa a moeda do título (`currencyCode` na terminologia de domínio).
- `paymentCurrencyCode` é opcional; quando ausente, assume `currency`.
- Valores monetários permanecem strings decimais e campos desconhecidos continuam rejeitados.

### Resposta

- Preserva todos os campos E2-S2.
- `paymentCurrencyCode`: sempre presente.
- `presentValue`: valor arredondado na moeda do título.
- `presentValueInPaymentCurrency`: sempre presente; valor final arredondado na moeda de pagamento.
- `exchangeRate`: nullable no OpenAPI; `null` para mesma moeda e, quando há conversão, objeto com `id`, `baseCurrencyCode`, `quoteCurrencyCode`, `rate`, `source`, `effectiveAt` e `createdAt`.
- O PV intermediário não arredondado é interno e nunca é exposto.

## Fórmulas e convenções

1. `PV_intermediário = valorNominal / (1 + taxaBase + spread)^(diasCorridos / 30)`, com `DECIMAL128`.
2. `presentValue = round(PV_intermediário, minorUnits(moedaTítulo), HALF_EVEN)`.
3. USD→BRL: `pagamentoIntermediário = PV_intermediário × taxa(USD/BRL)`.
4. BRL→USD: `pagamentoIntermediário = PV_intermediário ÷ taxa(USD/BRL)`.
5. `presentValueInPaymentCurrency = round(pagamentoIntermediário, minorUnits(moedaPagamento), HALF_EVEN)`.
6. Mesma moeda: `presentValueInPaymentCurrency = presentValue`, sem consulta, validade ou telemetria FX e com `exchangeRate=null`.
7. Nunca converter `presentValue` já arredondado, criar taxa 1,0, persistir inversa, usar `double`/`float` ou expor intermediários.

## Vigência, validade e erros

- A taxa aplicável satisfaz `effectiveAt <= Clock.instant()` e a ordenação total existente `effective_at DESC, created_at DESC, id DESC`.
- A idade é medida exclusivamente desde `effectiveAt`; `createdAt` é apenas o instante de persistência.
- Janela default de 15 minutos, configurável por `srm.fx-rate.max-age` / `FX_RATE_MAX_AGE` e reduzível em testes.
- Fronteira inclusiva: válida quando `Clock.instant() <= effectiveAt + validade`; expirada somente depois da fronteira.
- Uma taxa antiga sincronizada recentemente continua expirada; taxa futura não é vigente.
- Ausência: `404 EXCHANGE_RATE_NOT_FOUND`. Expiração: `422 EXCHANGE_RATE_EXPIRED`.
- Ambos interrompem o fluxo sem valor convertido nem resultado parcial; não disparam sync automático.
- ProblemDetail não expõe internals. Métricas distinguem `not_found` e `expired` com tags de baixa cardinalidade. Logs estruturados incluem par, idade e `exchangeRateId` quando existir.

## Casos financeiros aprovados

Snapshot de referência, usado somente como fixture de teste/smoke:

- `id=55555555-5555-4555-8555-555555555555`
- par `USD/BRL`, `rate=5.13000000`, `source=REFERENCE_CASE`
- `effectiveAt=2026-01-05T11:50:00Z`, `createdAt=2026-01-05T11:51:00Z`
- `Clock=2026-01-05T12:00:00Z`

Resultados obrigatórios:

- Cheque/USD da E2-S2: PV intermediário `2391.575917874497...`; `presentValue=2391.58 USD`; multiplicar o intermediário por `5.13`; `presentValueInPaymentCurrency=12268.78 BRL`. Converter `2391.58` produziria incorretamente `12268.81` e deve ser detectado por teste.
- Duplicata/BRL da E2-S2: PV intermediário `974.807074689671...`; `presentValue=974.81 BRL`; dividir o intermediário por `5.13`; `presentValueInPaymentCurrency=190.02 USD`.
- Mesma moeda: preserva o caso E2-S2, `presentValueInPaymentCurrency=presentValue` e `exchangeRate=null`.

## Critérios de aceite

- **AC1 — Contrato compatível:** payload E2-S2 sem `paymentCurrencyCode` continua aceito e assume a moeda do título; a resposta preserva os campos existentes e inclui os três campos aprovados com decimais como strings e `exchangeRate` nullable.
- **AC2 — Precisão e direção:** o PV não arredondado em `DECIMAL128` é convertido antes do único arredondamento final na moeda de pagamento; USD→BRL multiplica e BRL→USD divide o snapshot USD/BRL, sem taxa inversa fictícia, reproduzindo exatamente `12268.78` e `190.02`.
- **AC3 — Vigência e validade:** a consulta usa `Clock.instant()`, ordenação total e validade inclusiva de 15 minutos desde `effectiveAt`; testes cobrem antes, fronteira, depois, taxa futura e `createdAt` recente incapaz de revalidar taxa antiga.
- **AC4 — Erros e atomicidade do resultado:** ausente retorna `404 EXCHANGE_RATE_NOT_FOUND`, expirada retorna `422 EXCHANGE_RATE_EXPIRED`, ambos com ProblemDetail seguro, logs/métricas distintos, zero resultado parcial e nenhuma sincronização automática.
- **AC5 — Mesma moeda e arquitetura:** mesma moeda não consulta FX, não valida expiração nem emite telemetria de conversão; retorna pagamento igual ao PV e snapshot nulo. `pricing` consome somente porta pública de `currency`, sem frameworks no domínio ou escrita.
- **AC6 — Regressão e operação:** casos E2-S2 permanecem idênticos; testes unitários/HTTP/PostgreSQL/ArchUnit, OpenAPI e quatro serviços Compose comprovam os dois sentidos, mesma moeda e falhas sem antecipar escopo.

## Matriz de casos, bordas e falhas

| Caso | Preparação | Resultado esperado |
|---|---|---|
| Compatibilidade E2-S2 | Sem `paymentCurrencyCode` | Default para `currency`; mesma resposta anterior acrescida dos campos aprovados |
| USD→BRL | Snapshot aprovado válido | Multiplica PV intermediário; `2391.58 USD`; `12268.78 BRL` |
| Duplo arredondamento | Converter `2391.58` em vez do intermediário | Teste falha porque `12268.81` não é aceito |
| BRL→USD | Mesmo snapshot USD/BRL válido | Divide PV intermediário; `974.81 BRL`; `190.02 USD` |
| Mesma moeda | BRL→BRL ou USD→USD | Sem porta/validade/telemetria FX; pagamento igual ao PV; snapshot nulo |
| Antes da fronteira | `now < effectiveAt + 15m` | Taxa válida |
| Na fronteira | `now = effectiveAt + 15m` | Taxa válida |
| Após a fronteira | `now > effectiveAt + 15m` | `422 EXCHANGE_RATE_EXPIRED`; zero resultado parcial |
| Persistência recente | `createdAt` recente e `effectiveAt` antigo | Continua expirada |
| Taxa futura | `effectiveAt > now` | Ignorada; `404 EXCHANGE_RATE_NOT_FOUND` se não houver anterior |
| Desempate | Mesmo `effectiveAt`/`createdAt` | Maior `id` |
| Taxa ausente | Nenhuma versão vigente | `404 EXCHANGE_RATE_NOT_FOUND`; sem sync |
| Moeda não catalogada | Título ou pagamento ausente | `400 CURRENCY_NOT_SUPPORTED` |
| Falha de infraestrutura | Consulta cambial falha | `500` seguro; sem internals ou resultado parcial |

</frozen-after-approval>

## Rastreabilidade

- **Épico/capacidade:** E2 — Precificação determinística; CAP-03.
- **Requisito central:** RF-05 — converter o valor presente para a moeda de pagamento com taxa vigente.
- **RNFs:** RNF-01 (precisão), RNF-04 (erros seguros), RNF-05/RNF-06 (testabilidade/reprodutibilidade) e RNF-12 (auditabilidade financeira).
- **Arquitetura:** ARQ-02/03 (módulos/hexagonal), ARQ-06 (decimal), ARQ-07 (BASE/QUOTE, conversão final e validade) e ARQ-09 (API/ProblemDetail).
- **ADRs:** 0001, 0003, 0004 e 0006; ADR-0002 para a consulta PostgreSQL e ADR-0007 para logs/métricas de baixa cardinalidade.
- **Predecessoras:** E0-S1, E0-S2, E1-S1, E1-S2, E1-S3, E2-S1 e E2-S2 `Done`.

## Code Map e arquivos previstos

- `backend/src/main/java/com/srm/creditengine/currency/domain/port/` — porta/resultado cambial públicos e erros tipados; reutilizar `CurrencyCode` e snapshot existente.
- `backend/src/main/java/com/srm/creditengine/currency/persistence/` — reutilizar seleção total e expor consulta aplicável sem modificar histórico.
- `backend/src/main/java/com/srm/creditengine/pricing/{domain,service}/` — preservar PV intermediário apenas internamente, converter e orquestrar após o cálculo E2-S2.
- `backend/src/main/java/com/srm/creditengine/pricing/api/` — evoluir DTOs, handler e OpenAPI de modo compatível.
- `backend/src/main/resources/application.yml` e `.env.example` — validade configurável, se necessária conforme padrão vigente.
- `backend/src/test/java/com/srm/creditengine/{currency,pricing,architecture}/` — unidade, HTTP, PostgreSQL/Testcontainers e limites modulares.
- `docs/api/contracts.md`, `README.md`, `AI_USAGE.md` e esta story — atualizar após implementação real; schema/ER/DDL não mudam.

## Tarefas técnicas ordenadas

- [x] **T1 (AC3–AC5):** definir por testes a porta cambial pública e o snapshot auditável; validar vigência/idade com `Clock` e erros tipados sem expor entidade/repository/service.
- [x] **T2 (AC2–AC4):** adaptar a consulta PostgreSQL ao snapshot USD/BRL reutilizável nos dois sentidos; provar ordenação, futura, fronteiras inclusivas, `createdAt` irrelevante e falhas em PostgreSQL 16/Testcontainers.
- [x] **T3 (AC2/AC5):** preservar o PV intermediário DECIMAL128 dentro do fluxo, implementar conversão multiplicar/dividir e arredondar uma única vez na moeda de pagamento; mesma moeda usa apenas o PV final.
- [x] **T4 (AC1/AC2/AC5):** integrar `CurrencyMetadataQuery`, default da moeda de pagamento e snapshot ao caso de uso, sem escrita, sync, taxa identidade ou inversa persistida.
- [x] **T5 (AC1/AC4):** evoluir request/response, ProblemDetail e OpenAPI; documentar `exchangeRate` nullable e mapear 404/422/500 sem internals ou resultado parcial.
- [x] **T6 (AC2–AC6):** cobrir casos aprovados, dupla perda de precisão, mesma moeda sem interação, erros/telemetria, ausência de aritmética binária e limites ArchUnit; preservar regressão E2-S2.
- [x] **T7 (AC6):** atualizar contratos/README/AI_USAGE/story, validar OpenAPI e Compose com fixture/smoke não produtivo, executar todos os gates e revisar o diff.

## Estratégia de testes e gates

- **Unitários:** direções, PV não arredondado, dupla perda de precisão, minor units/HALF_EVEN, mesma moeda e Clock fixo antes/na/depois da fronteira.
- **HTTP/OpenAPI:** compatibilidade sem campo novo, request explícito, snapshot completo/nullable, strings decimais, 200/400/404/422/500 e ProblemDetail seguro sem resultado parcial.
- **PostgreSQL/Testcontainers:** snapshot aprovado como fixture, seleção vigente/futura/desempates, expiração por `effectiveAt`, `createdAt` irrelevante e zero escrita.
- **Arquitetura:** apenas a porta pública cruza `pricing→currency`; domínio não depende de Spring/JPA/Jackson/big-math adapter e não usa `double`/`float`.
- **Observabilidade:** métricas distintas para ausente/expirada, tags limitadas e logs com par, idade e id quando disponível; nenhuma telemetria FX para mesma moeda.
- **Compose:** config/build, PostgreSQL/WireMock/backend/frontend healthy; cadastrar o snapshot apenas como dado efêmero de smoke; validar os dois sentidos, mesma moeda, ausente e expirada; `down` sem `-v`.
- **Gates:** backend `spotless:check` e `verify` (JaCoCo/ArchUnit/Testcontainers); frontend `npm ci`, lint, typecheck, testes e build; `check-docs.sh . story`; `git diff --check`.

## Riscos e dependências

- Duplo arredondamento altera centavos; mitigado pelo caso `12268.78` versus `12268.81`.
- Direção invertida corrompe a conversão; mitigada por testes simétricos com um único snapshot USD/BRL.
- Usar `createdAt` revalida indevidamente taxa antiga; mitigado por Clock fixo e teste específico.
- Evolução do DTO pode quebrar clientes; mitigada pela preservação da chave legada `currency` e default do novo campo.
- Snapshot de referência pode ser confundido com seed produtivo; ele é exclusivamente fixture/smoke efêmero.
- Dependências atendidas: catálogo/moedas/minor units, câmbio append-only e consulta vigente, Clock, Strategy, taxa-base, calendário, cálculo E2-S2, precisão e guardrails.
- Nenhuma decisão de negócio ou arquitetura permanece aberta para iniciar a implementação.

## Definition of Ready

- [x] Precedência E2-S3 confirmada; todas as predecessoras estão Done.
- [x] RF-05, CAP-03, ARQ-07 e ADR-0004 identificados.
- [x] Baseline e contratos concluídos inspecionados e reutilizados.
- [x] D1–D6 aprovadas e incorporadas de forma verificável.
- [x] Compatibilidade do campo legado `currency` explicitada.
- [x] Aprovação humana para Ready for Dev registrada em 2026-09-27.

**Resultado da DoR:** integralmente atendida; story aprovada humanamente e pronta para desenvolvimento.

## Definition of Done

- [x] AC1–AC6 atendidos com evidências reais e regressão E2-S2.
- [x] Casos aprovados retornam exatamente `12268.78 BRL`, `190.02 USD` e igualdade na mesma moeda.
- [x] Teste comprova conversão do PV não arredondado e rejeita o resultado incorreto `12268.81`.
- [x] Ausente/expirada, fronteiras temporais, taxa futura, sentidos e mesma moeda são comprovados sem escrita/sync/resultado parcial.
- [x] OpenAPI declara snapshot nullable; ProblemDetail, logs e métricas correspondem ao runtime.
- [x] ArchUnit, Spotless, verify/JaCoCo/Testcontainers e regressão frontend passam.
- [x] Compose/smokes e gate documental passam; documentação reflete o runtime.
- [ ] Revisão não deixa Bloqueante/Importante; aprovação humana final antecede Done.

## Dev Agent Record

### File List

- `.env.example`, `backend/src/main/resources/application.yml` — janela cambial configurável.
- `backend/src/main/java/com/srm/creditengine/currency/domain/port/{ApplicableExchangeRate,ApplicableExchangeRateQuery,ExchangeRateExpiredException,ExchangeRateNotFoundException,ExchangeRateQueryException}.java` — contrato público e falhas tipadas.
- `backend/src/main/java/com/srm/creditengine/currency/service/ApplicableExchangeRateService.java` — vigência, validade, logs e métricas.
- `backend/src/main/java/com/srm/creditengine/pricing/{api,domain,service}/` — contrato REST, PV bruto, conversão e orquestração cross-currency.
- `backend/src/main/java/com/srm/creditengine/pricing/api/PricingOpenApiConfiguration.java` — nulabilidade explícita do snapshot no OpenAPI 3.1.
- `backend/src/test/java/com/srm/creditengine/{architecture,currency,pricing}/` — provas unitárias, HTTP, PostgreSQL e ArchUnit.
- `README.md`, `AI_USAGE.md`, `docs/api/contracts.md`, `docs/observability.md` — contrato, operação e rastreabilidade.
- `_bmad-output/implementation-artifacts/e2-s3-simular-cross-currency.md` — evidências e promoção para Review.
- Os arquivos de produção, testes e documentação acima foram distribuídos pela autora nos commits `2e1cad7`, `e42c89d`, `c992ffb` e `4dde935`; este registro documental posterior permanece fora desses commits.
- A correção de seleção cambial e suas provas/documentação foram executadas posteriormente pela autora nos commits `f328e7f`, `af07f29` e `950c490`; o agente não executou esses commits.

### Completion Notes

- D1–D6 foram aprovadas e incorporadas ao contrato, precisão, validade, erros, mesma moeda e casos financeiros.
- A nomenclatura `currencyCode` da decisão foi conciliada com a chave JSON existente `currency`, preservada para compatibilidade com E2-S2.
- O snapshot fixo foi classificado como fixture de teste/smoke, não como seed de produção.
- Implementada conversão sobre o PV DECIMAL128 não arredondado, com um único snapshot USD/BRL nos dois sentidos e sem FX para mesma moeda.
- OpenAPI declara `exchangeRate` nullable; HTTP comprova 404/422/500 seguros e ausência de resultado parcial.
- A auditoria ArchUnit eliminou o vazamento inicial de tipos internos de `currency`, mantendo apenas o contrato em `domain.port`.
- `spotless:check` e `verify` passaram em 2026-09-28 com 138 testes; regressão frontend passou em cópia temporária limpa porque um processo do editor bloqueou o `node_modules` local.
- Compose foi reconstruído com os quatro serviços healthy; smokes reais retornaram `12268.78 BRL`, `190.02 USD` e `974.81`/snapshot nulo para mesma moeda.
- A validade cambial foi comprovada antes e exatamente na fronteira inclusiva, após a expiração, com taxa futura ignorada e com `createdAt` recente incapaz de renovar `effectiveAt` antigo.
- A revisão crítica encontrou e corrigiu um falso verde de nulabilidade no OpenAPI, validação ausente da janela cambial, asserções incompletas do snapshot e ambiguidades documentais.
- O agente não executou commits; posteriormente, a autora realizou `2e1cad7 feat(currency): expose applicable exchange-rate snapshots`, `e42c89d feat(pricing): add cross-currency simulation`, `c992ffb test(pricing): prove cross-currency simulation contracts` e `4dde935 docs(pricing): document cross-currency simulation`.
- A correção da revisão passou a avaliar direto e reverso antes da decisão: direto válido mantém precedência; direto ausente, futuro ou expirado cede ao reverso válido; ausência e expiração permanecem semanticamente distintas, sem criar taxa inversa ou efeito colateral.
- As três Sugestões opcionais da revisão final permaneceram deliberadamente fora desta correção.
- Posteriormente, a autora registrou a correção nos commits `f328e7f fix(currency): evaluate both exchange-rate orientations`, `af07f29 test(currency): prove exchange-rate orientation fallback` e `950c490 docs(currency): document exchange-rate orientation selection`; nenhum deles foi executado pelo agente.

### Evidências

- Planejamento: épicos, PRD, matriz de rastreabilidade, Architecture Spine e ADR-0004.
- Baseline: contrato e implementação concluídos de E1-S1/E1-S2/E1-S3/E2-S1/E2-S2.
- Decisão humana de 2026-09-27: D1–D6 aprovadas e promoção para Ready for Dev autorizada.
- Backend: `spotless:check` e `verify` verdes com 138 testes, incluindo ArchUnit e PostgreSQL 16/Testcontainers; JaCoCo registrou 829/858 linhas (96,62%) e 163/204 branches (79,90%) no agregado.
- Auditoria: OpenAPI nullable/campos do snapshot; 404/422/500; mesma moeda sem porta/métrica FX; taxa futura ignorada.
- Frontend: `npm ci`, lint, typecheck, 14 testes e build verdes na cópia temporária limpa; cobertura 100% linhas/statements e 87,5% branches.
- Compose: imagem final reconstruída; PostgreSQL, WireMock, backend e frontend healthy; readiness/frontend/Swagger HTTP 200; OpenAPI 3.1 expõe `exchangeRate.oneOf=[snapshot,null]`; `down` executado sem remoção de volumes.
- Smokes finais: USD→BRL `12268.78`, BRL→USD `190.02`, mesma moeda `974.81` com `exchangeRate=null`.
- Validade: testes com `Clock` fixo provam taxa válida antes e em `effectiveAt + maxAge`, expirada depois da fronteira, taxa futura não vigente e irrelevância de `createdAt` para renovar a idade.
- Histórico humano validado: `2e1cad7` (porta/snapshot cambial), `e42c89d` (simulação cross-currency), `c992ffb` (provas de contrato) e `4dde935` (documentação); nenhum deles foi executado pelo agente.
- Correção da seleção: testes cobrem direto expirado + reverso válido, ambos válidos, direto ausente + reverso válido, direto expirado + reverso ausente, ambos expirados, ambos ausentes e futuro + reverso válido; integração PostgreSQL/HTTP comprova snapshot original e multiplicação/divisão conforme a orientação escolhida.
- Gates corretivos locais: Spotless e `verify` verdes com 145 testes, zero falhas/erros/ignorados; JaCoCo 838/867 linhas e 168/212 branches; ArchUnit e Testcontainers incluídos no `verify`.
- Regressão frontend verde em cópia temporária limpa com 14 testes, lint, typecheck e build; o bloqueio `EPERM` do `node_modules` local permanece como limitação operacional já registrada.
- Compose final: quatro serviços healthy; smoke BRL→USD com direto expirado selecionou o reverso USD/BRL válido e retornou `190.02`; ambiente encerrado com `down` sem `-v`.
- Histórico corretivo validado: `f328e7f` implementa a avaliação das duas orientações, `af07f29` prova o fallback e a precedência, e `950c490` documenta a política; os commits foram executados posteriormente pela autora, nunca pelo agente.

## Review Record

- 2026-09-26 — Preparação documental inicial em Draft.
- 2026-09-27 — Revisão humana aprovou D1–D6, compatibilidade, casos financeiros e prontidão para implementação.
- 2026-09-27 — Implementação mantida em Review após backend, frontend, Compose, smokes, OpenAPI e documentação verdes; aprovação humana final permanece pendente.
- 2026-09-28 — Histórico inspecionado: a autora executou posteriormente os commits `2e1cad7`, `e42c89d`, `c992ffb` e `4dde935`; a story permanece em Review e aguarda aprovação humana final.
- 2026-09-28 — Revisão final contra `origin/main...HEAD`: jobs remotos `backend`, `frontend` e `repository` confirmados como aprovados pela autora. AC1, AC2, AC4, AC5 e AC6 atendidos; AC3 requer correção da precedência entre orientações quando a cotação direta está expirada e existe snapshot reverso válido. Resultado: nenhum Bloqueante, um Importante e três Sugestões; recomendação **Corrigir antes do merge**.
- 2026-09-28 — Achado Importante corrigido e revisado localmente: ambas as orientações são avaliadas, a direta válida mantém precedência e o reverso válido é reutilizado quando a direta está ausente, futura ou expirada. Nenhum Bloqueante ou Importante permanece no diff corretivo; as três Sugestões continuam não implementadas. Story mantida em Review para nova CI e aprovação humana.
- 2026-09-28 — A autora executou posteriormente os commits corretivos `f328e7f`, `af07f29` e `950c490`. O achado Importante de precedência direta/reversa está resolvido, os gates corretivos passaram com 145 testes e o smoke comprovou direto expirado + reverso válido. Não restam Bloqueantes ou Importantes; as três Sugestões seguem deliberadamente não implementadas e a story permanece em Review.

### Resultado da revisão final

- **Bloqueantes:** nenhum.
- **Importantes:** nenhum restante; o achado de precedência direta/reversa foi corrigido e coberto por unidade, PostgreSQL/HTTP e smoke na imagem final.
- **Sugestões:** reutilizar `CurrencyMetadata` na mesma moeda para evitar consulta duplicada; tornar a asserção OpenAPI estrita sobre o `oneOf` da propriedade em vez de aceitar representações alternativas; adicionar uma prova HTTP/PostgreSQL composta para expiração/futuro e ausência de escrita no caminho cross-currency, embora as garantias já estejam cobertas separadamente.
- **Checks remotos:** `backend`, `frontend` e `repository` aprovados no GitHub Actions, conforme confirmação humana.
- **Limitações remanescentes:** bloqueio local anterior do `node_modules` continua registrado; documentos de escala e EDA permanecem pendentes apenas para release; a lente automatizada de bordas não conseguiu ler o prompt renderizado no subprocesso, e os mesmos casos foram auditados manualmente no código e nos testes.
- **Recomendação final:** correção pronta para nova execução dos jobs remotos e aprovação humana; a story permanece em Review.

## Review Triage Log

| Origem | Achado | Veredito e evidência | Rota |
|---|---|---|---|
| blind | Estado textual congelado diverge do front matter | `low`: o front matter `in-review` é autoritativo; o texto `Ready for Dev` está no bloco congelado e não pode ser reescrito nesta execução. | rejeitado |
| blind | T7 e DoD contradiziam as evidências | `medium`: gates e documentação já estavam concluídos; checklists foram sincronizados após a execução final. | patch |
| blind | `calculationDate` parecia governar também o FX | `medium`: contrato era ambíguo; agora separa data econômica de `Clock.instant()`. | patch |
| blind | Observabilidade proibia IDs em logs e depois registrava `exchangeRateId` | `medium`: a frase foi corrigida para proibir alta cardinalidade em tags, preservando o ID auditável no log. | patch |
| blind | Busca direta antes da reversa poderia usar BRL/USD | `false`: não há inversão criada; cada resultado é snapshot persistido e a ordenação total é definida dentro de cada par. | rejeitado |
| blind/edge | `maxAge` aceitava zero ou negativo | `medium`: configuração inválida expiraria taxas incorretamente; validação fail-fast e testes foram adicionados. | patch |
| blind/edge | Fronteiras e `createdAt` não eram todas testadas no PostgreSQL | `false`: SQL prova vigência/futuro/desempate; fronteira de idade e irrelevância de `createdAt` pertencem ao serviço e são cobertas com `Clock` determinístico. | rejeitado |
| blind | Fixture HTTP usa relógio real | `low`: usa offsets relativos e não afeta valores financeiros; os casos temporais exatos permanecem determinísticos em testes unitários. | rejeitado |
| blind/verification/intent | Snapshot BRL→USD e campos auditáveis incompletamente assertados | `medium`: ambos os sentidos agora verificam o mesmo snapshot e todos os campos observáveis. | patch |
| verification | `paymentCurrencyCode` explícito não era assertado | `medium`: respostas USD→BRL e BRL→USD agora verificam a moeda de pagamento. | patch |
| verification | Override da validade não era exercitado | `medium`: teste com janela não padrão de um minuto prova fronteira inclusiva e expiração. | patch |
| edge | Duas orientações poderiam competir por ordenação global | `false`: o contrato não define ordenação entre pares distintos; a consulta usa o par direto persistido e só recorre ao reverso quando ausente. | rejeitado |
| intent | Evidências estavam distribuídas entre unidade, HTTP e PostgreSQL | `false`: é a pirâmide de testes deliberada; o conjunto cobre precisão, contrato e persistência sem duplicar todas as provas em E2E. | rejeitado |
| blind-final | String vazia em `paymentCurrencyCode` passaria pela validação | `false`: `@Pattern("[A-Z]{3}")` aceita `null`, mas rejeita string vazia; a omissão continua sendo o único comportamento opcional necessário. | rejeitado |
| blind-final | `null` explícito diverge de campo opcional omitido | `false`: o contrato não proíbe nulabilidade de entrada e o mapper aplica o mesmo default aprovado nos dois casos. | rejeitado |
| blind-final | Cotação direta expirada oculta cotação reversa válida | `medium`, resolvido: o serviço agora lê ambos os pares, filtra futuro/expirado e escolhe direto válido antes do reverso válido; matriz unitária, PostgreSQL/HTTP e smoke comprovam o comportamento. | corrigido |
| blind-final | Não há ordenação global entre orientações | `false` (carried): o contrato não exige competição global entre dois pares persistidos; o defeito real e mais estreito é a cotação direta expirada impedir o fallback para o snapshot reverso válido. | rejeitado |
| blind-final | Snapshot público não repete invariantes de `ExchangeRate` | `false`: o adapter runtime constrói o snapshot a partir de `ExchangeRate`, que valida moedas, taxa, origem e instantes; uma implementação inválida da porta violaria o contrato interno. | rejeitado |
| blind-final | Snapshot inválido causaria falha não segura em `ExchangeConversion` | `false`: no runtime o snapshot vem do domínio validado e qualquer exceção inesperada ainda é convertida pelo advice de pricing em 500 seguro sem internals. | rejeitado |
| blind-final | Mesma moeda consulta metadata duas vezes | `low`: não consulta câmbio nem emite telemetria FX, mas repete uma leitura idêntica e amplia desnecessariamente a superfície de falha. | sugestão |
| blind-final | Resumo OpenAPI ainda menciona apenas moeda do recebível | `low`: descrição está incompleta, porém o schema e o contrato descrevem corretamente a moeda de pagamento e a conversão. | sugestão |
| blind-final | Teste de nulabilidade OpenAPI aceita alternativas amplas | `low`: o runtime foi comprovado como `oneOf=[snapshot,null]`, mas uma asserção estrita reduziria o risco de falso verde futuro. | sugestão |
| blind-final | Fronteiras temporais não são todas repetidas no PostgreSQL | `false`: seleção/vigência e taxa futura são cobertas no PostgreSQL; idade inclusiva e irrelevância de `createdAt` são regras do service verificadas deterministicamente com `Clock`. | rejeitado |
| blind-final | Ausência de sincronização automática não foi verificada por mock do provider | `false`: o fluxo depende somente de `ApplicableExchangeRateQuery` e não possui provider, writer ou caso de uso de sync; a ausência é estrutural e coberta pelo limite arquitetural. | rejeitado |
| blind-final | Prova de ausência de telemetria na mesma moeda seria incompleta | `false`: não há chamada à porta FX e o código não contém emissão de log/métrica FX no ramo de mesma moeda; o teste também comprova ausência da métrica implementada. | rejeitado |
| blind-final | Smoke não possui script versionado | `false`: a story exige execução e evidência, não um novo artefato produtivo; os resultados reais e o encerramento do Compose estão registrados. | rejeitado |
| intent-final | Validade e ausência de escrita estão provadas em superfícies separadas | `low`: a pirâmide combina HTTP, unidade e PostgreSQL; uma prova composta adicional melhoraria legibilidade, sem invalidar as garantias existentes. | sugestão |

## Change Log

- 2026-09-26 — Story E2-S3 criada em Draft com precedência, escopo, matriz, testes e D1–D6 explícitas; nenhum código alterado.
- 2026-09-27 — D1–D6 incorporadas; contrato, precisão, validade, erros, mesma moeda, casos, tarefas, testes e DoR sincronizados; status promovido para Ready for Dev por aprovação humana.
- 2026-09-27 — E2-S3 implementada e auditada: conversão cross-currency, validade, erros seguros, observabilidade, OpenAPI e testes backend; status promovido para Review.
- 2026-09-27 — Revisão BMAD corrigiu falso verde OpenAPI, configuração temporal, provas do snapshot e documentação; nenhum Bloqueante ou Importante permaneceu aberto.
- 2026-09-28 — Commits humanos `2e1cad7`, `e42c89d`, `c992ffb` e `4dde935` registrados após validação do histórico; Completion Notes, File List, evidências e Review Record sincronizados sem alterar o status Review.
- 2026-09-28 — Revisão final contra `origin/main...HEAD` registrou checks remotos aprovados, um achado Importante na precedência direta/reversa, sugestões opcionais e recomendação **Corrigir antes do merge**; story preservada em Review.
- 2026-09-28 — Achado Importante corrigido: seleção avalia direto/reverso elegíveis com precedência determinística; 145 testes backend, regressão frontend, Compose e smoke passaram; Sugestões não implementadas e status Review preservado.
- 2026-09-28 — Commits humanos corretivos `f328e7f`, `af07f29` e `950c490` registrados: achado Importante resolvido, nenhum Bloqueante ou Importante restante, três Sugestões deliberadamente não implementadas e story mantida em Review.

---
title: 'E2-S2 — Simular em moeda do título'
type: 'feature'
created: '2026-09-26'
status: 'in-review'
baseline_commit: '88d2e15903719da8ba7f9fafdecdea04e845ec85'
route: 'full'
route_source: 'auto'
review: 'thorough'
review_source: 'auto'
lenses_ran: ['blind-hunter', 'edge-case-hunter', 'verification-gap', 'intent-alignment']
review_loop_iteration: 0
context:
  - 'docs/adr/0001-adotar-monolito-modular-hexagonal.md'
  - 'docs/adr/0002-adotar-postgresql-flyway-jpa-jooq.md'
  - 'docs/adr/0003-padronizar-calculo-financeiro-decimal.md'
  - 'docs/adr/0006-padronizar-contratos-e-erros-http.md'
  - 'docs/adr/0007-adotar-observabilidade-e-resiliencia-seletiva.md'
---

# E2-S2 — Simular em moeda do título

**Status:** Review

<frozen-after-approval reason="intenção e critérios pertencem à responsável humana">

## Objetivo e valor

Permitir simular, sem liquidar ou persistir a simulação, o valor presente de um título na própria moeda. O incremento conecta o catálogo e as Strategies da E2-S1 à taxa base vigente da E1-S3 e entrega cálculo determinístico e auditável para reutilização posterior.

## Escopo e contratos

- `POST /api/v1/pricing/simulations`, sucesso `200 application/json`.
- Requisição: `faceValue` (string decimal positiva, até 2 casas), `currency` (`CurrencyCode`), `receivableTypeCode`, `calculationDate` (`LocalDate`, obrigatória) e `dueDate` (`LocalDate`, obrigatória). Campos desconhecidos são rejeitados.
- `calculationDate` é a data econômica; `Clock` serve somente a metadados técnicos e nunca influencia cálculo, vigência ou calendário.
- Resposta: `faceValue`, `currency`, `receivableTypeCode`, `calculationDate`, `dueDate`, `adjustedDueDate`, `termDays`, `termMonths`, `baseRate`, `baseRateId`, `baseRateSource`, `spread`, `monthlyRate`, `presentValue` e `discount`; dinheiro/taxas são strings decimais.
- Calendário versionado ANBIMA 2025–2030, metadados `source=ANBIMA`, período e data de atualização; sem chamada externa em runtime.
- `CurrencyMetadataQuery` interna ao módulo `currency`, retornando `CurrencyCode` e `minorUnits`; sem endpoint.

**Fora de escopo:** câmbio ou moeda de pagamento diferente (E2-S3); persistência de simulação/recebível/snapshot; liquidação, settlement, frontend, catálogo REST, novas taxas base/Strategies ou calendário obtido em runtime.

## Fórmula e convenções financeiras

1. `adjustedDueDate` é o primeiro dia útil igual ou posterior a `dueDate`, segundo `BusinessCalendar`/`BrazilBusinessCalendar`; sábado, domingo e feriados bancários ANBIMA, incluindo Carnaval e Corpus Christi, não são úteis.
2. Comparar datas somente após o ajuste: anterior a `calculationDate` falha; igual é prazo zero válido.
3. `termDays = DAYS.between(calculationDate, adjustedDueDate)`; `termMonths = termDays / 30` com `MathContext.DECIMAL128`.
4. `monthlyRate = baseRate + spread`; `PV = faceValue / (1 + monthlyRate)^termMonths`; `discount = faceValue - PV`.
5. `DecimalPower` isola `BigDecimalMath.pow(base, exponent, MathContext.DECIMAL128)` de `ch.obermuhlner:big-math`, com versão literal fixada no Maven. O domínio não importa a biblioteca; base deve ser positiva; expoente zero devolve exatamente `BigDecimal.ONE`; nunca há conversão para `double`/`float`.
6. Todos os intermediários usam DECIMAL128. `presentValue` e `discount` recebem único arredondamento monetário final nos minor units consultados, com `HALF_EVEN`.

## Critérios de aceite

- **AC1 — Contrato:** dada requisição válida na moeda do título, quando simulada, então retorna `200` com todos os campos definidos no contrato, decimais como strings e OpenAPI coerente; `calculationDate` fornecida governa todo o cálculo.
- **AC2 — Calendário e prazo:** dado vencimento em dia não útil coberto, quando simulado, então avança ao próximo dia útil ANBIMA e calcula ACT/30 por dias corridos; ano fora de 2025–2030 retorna `422 BUSINESS_CALENDAR_NOT_AVAILABLE` sem resultado parcial.
- **AC3 — Precisão:** dados os três casos aprovados, quando calculados, então os valores monetários finais coincidem exatamente; potência, taxas e intermediários permanecem decimais/DECIMAL128 e somente o resultado monetário final usa `HALF_EVEN`.
- **AC4 — Colaborações:** a simulação usa `ReceivableTypePricingResolver`, `PricingStrategy`, `BaseRateQuery`, `CurrencyCode` e `CurrencyMetadataQuery`, sem acessar registry/JPA diretamente nem selecionar moeda/tipo por `if`/`switch`; erros existentes de tipo, Strategy, moeda e taxa são preservados e interrompem o cálculo.
- **AC5 — Validação e efeitos:** valor/campos inválidos retornam `400 VALIDATION_ERROR`; vencimento ajustado anterior retorna `422 DUE_DATE_BEFORE_CALCULATION_DATE`; prazo zero retorna PV nominal/deságio zero; erros RFC 9457 não expõem internals e nenhuma linha de negócio é criada.
- **AC6 — Dados, arquitetura e operação:** migration posterior a V4 restringe `currency.minor_units` a 0–6 preservando BRL/USD=2; portas e dependências obedecem `api → service → domain ← adapters`; Compose mantém quatro serviços healthy e smoke prova cálculo e ausência de persistência.

## Casos financeiros aprovados

| Caso | Tipo/moeda | Nominal | Data cálculo → vencimento ajustado | Base + spread | Expoente | PV final | Deságio |
|---|---|---:|---|---|---:|---:|---:|
| 1 | `DUPLICATA_MERCANTIL`/BRL | 1000.00 | 2026-01-02 → 2026-02-02 | 0.010000000000 + 0.015 = 0.025 | 31/30 | 974.81 | 25.19 |
| 2 | `CHEQUE_PRE_DATADO`/USD | 2500.00 | 2026-01-05 → 2026-02-19 | 0.005000000000 + 0.025 = 0.030 | 45/30 | 2391.58 | 108.42 |
| 3 | qualquer combinação válida | 1000.00 | mesma data após ajuste | taxas aplicáveis | 0 | 1000.00 | 0.00 |

Intermediários de referência: caso 1, PV ≈ `974.807074689671`; caso 2, PV ≈ `2391.575917874497`. Testes documentam tolerância somente para intermediários; dinheiro final exige igualdade exata.

## Matriz de casos, bordas e falhas

| Caso | Estado/entrada | Resultado |
|---|---|---|
| Dia útil | vencimento útil 2025–2030 | data inalterada; fórmula aplicada |
| Sábado/domingo/feriado | dia não útil, inclusive Carnaval/Corpus Christi | primeiro dia útil posterior |
| Virada de ano | ajuste cruza o ano dentro da cobertura | resultado correto |
| Ano não configurado | data consultada fora de 2025–2030 | `BUSINESS_CALENDAR_NOT_AVAILABLE`, 422 |
| Prazo zero | ajustado = cálculo | expoente 0, PV=nominal, deságio=0 |
| Prazo negativo | ajustado < cálculo | `DUE_DATE_BEFORE_CALCULATION_DATE`, 422, sem resultado parcial |
| Prazo inteiro/fracionário | 30 / 31 / 45 dias | potência decimal determinística |
| Potência inválida | base não positiva/falha da biblioteca | `PRICING_CALCULATION_FAILED`, 500 seguro |
| Tipo/Strategy inválidos | ausente, inativo ou não configurado | códigos aprovados; cálculo interrompido |
| Moeda/taxa inválida | malformada, não catalogada ou sem versão vigente | códigos existentes; cálculo interrompido |
| Entrada HTTP inválida | nula, não positiva, escala >2, campo ausente/desconhecido | `VALIDATION_ERROR`, 400, violações por campo |
| Repetição | mesma entrada e referências | resposta idêntica; contagens do banco inalteradas |

</frozen-after-approval>

## Rastreabilidade

| Origem | Cobertura |
|---|---|
| Épico E2 / CAP-02 | precificação por risco determinística na moeda do título |
| RF-03 / E2-S1 | Strategy e spread por tipo |
| RF-04 | simulação de VP sem persistir liquidação |
| RF-12 / RNF-04 | OpenAPI, validação e `ProblemDetail` seguro |
| RNF-01 / RNF-10 / RNF-11 | precisão decimal, testes e guardrails |
| RNF-07 / ADR-0007 | timer e logs estruturados de resultado, sem alta cardinalidade |
| RNF-09 | Compose e smoke local |
| ADR-0001/0002 | limites modulares, Flyway e PostgreSQL 16 |
| ADR-0003 | ACT/30, ANBIMA configurável, DECIMAL128, potência decimal e HALF_EVEN |
| ADR-0006 | DTOs, API v1 e RFC 9457 |

## Tarefas técnicas ordenadas

- [x] **T1 (AC6):** criar V5 para substituir a constraint 0–8 por 0–6 sem alterar V2, provar catálogo BRL/USD=2 em PostgreSQL 16 e atualizar DDL/ER/modelo.
- [x] **T2 (AC4/AC6):** criar `CurrencyMetadataQuery` e adapter de leitura, com tradução de infraestrutura e guardrail da única exposição autorizada.
- [x] **T3 (AC2):** criar `BusinessCalendar`, `BrazilBusinessCalendar` e recurso versionado ANBIMA 2025–2030 com metadados; testar útil, sábado, domingo, feriado, virada e indisponibilidade.
- [x] **T4 (AC3):** fixar versão exata validada de `big-math`, encapsular em `DecimalPower` interno e implementar VOs/cálculo puro conforme DECIMAL128, expoente zero e base positiva.
- [x] **T5 (AC1/AC4/AC5):** orquestrar a simulação com as portas existentes, validação após ajuste e zero escrita; instrumentar `srm.pricing.duration` e logs estruturados de resultado/código, sem valores de alta cardinalidade.
- [x] **T6 (AC1/AC5):** implementar request/response/controller/OpenAPI e integrar o handler RFC 9457 com 400/404/422/500 aplicáveis.
- [x] **T7 (AC1–AC6):** implementar testes unitários, contrato, OpenAPI, integração/Testcontainers e ArchUnit; atualizar documentação acionada e executar todos os gates.

## Code Map e arquivos previstos

- `backend/pom.xml` — versão literal de `ch.obermuhlner:big-math` validada pelo Maven.
- `backend/src/main/resources/db/migration/V5__restrict_currency_minor_units.sql` — substitui constraint atual por 0–6; V2 permanece imutável.
- `backend/src/main/resources/calendars/` — calendário ANBIMA 2025–2030 e metadados versionados.
- `backend/src/main/java/com/srm/creditengine/currency/domain/port/` e `currency/persistence/` — metadata port/adapter internos.
- `backend/src/main/java/com/srm/creditengine/pricing/domain/` — `BusinessCalendar`, `DecimalPower`, comando/resultado, cálculo e erros puros.
- `backend/src/main/java/com/srm/creditengine/pricing/service/` — calendário, potência adapter e orquestração.
- `backend/src/main/java/com/srm/creditengine/pricing/api/` — endpoint, DTOs, OpenAPI e integração com erro global.
- `backend/src/test/java/com/srm/creditengine/{currency,pricing}/` e fixtures — domínio, calendário, HTTP, PostgreSQL, OpenAPI e arquitetura.
- `docs/database/{ddl.sql,er.md,data-model.md}`, `docs/api/contracts.md`, `docs/observability.md`, `README.md`, `AI_USAGE.md` — documentos acionados pela implementação real.

## Estratégia de testes e gates

- Unitários parametrizados: três referências aprovadas, tolerância intermediária explícita, dinheiro exato, datas, potência, determinismo, base inválida, limites e todos os erros da matriz.
- Contrato: request obrigatório, strings decimais, campos da resposta, `200/400/404/422/500`, OpenAPI e `ProblemDetail` sem stack/SQL/classes.
- Integração PostgreSQL 16/Testcontainers: V5/constraint, seeds/minor units, taxa vigente/futura, tipo real e contagem das tabelas antes/depois.
- Arquitetura: domínio puro e sem `big-math`, dependências/portas exatas, proibição de binários, atalhos de tipo/moeda e endpoint de metadata.
- Compose: `config`, `up --build -d`, quatro serviços healthy, smoke dos casos 1 e 3, métrica/log observáveis, ausência de escrita e `down` sem `-v`.
- Gates: backend Spotless e `verify` (JaCoCo, ArchUnit, Testcontainers); frontend `npm ci`, lint, typecheck, testes e build como regressão; gate documental story e `git diff --check`.

## Riscos e dependências

- E0-S1/E0-S2, E1-S1/E1-S2/E1-S3 e E2-S1 estão `Done`; os contratos citados já existem.
- O catálogo já contém `minor_units NOT NULL`, BRL/USD=2 e faixa 0–8 em V2; V5 deve apenas estreitar a constraint, sem reescrever migration aplicada ou duplicar seeds.
- A lista ANBIMA deve ser transcrita e revisada com fonte/data rastreáveis; cobertura incompleta altera prazo e centavos.
- Versão de `big-math` deve ser literal, resolvida pelo Maven e registrada; mudança futura exige repetir testes de conformidade.
- Guardrails atuais enumeram portas públicas e precisam ser ampliados deliberadamente, nunca relaxados.

## Decisões aprovadas

- D1–D6 foram aprovadas humanamente em 2026-09-26 e estão incorporadas no contrato, fórmula, ACs, matriz, tarefas e testes. Não restam decisões bloqueantes para iniciar.

## Definition of Ready

- [x] Precedência, dependências, RF/RNF/ADRs e baseline identificados.
- [x] Escopo separado de E2-S3 e stories posteriores.
- [x] Contrato, fórmula, referências, erros, tarefas, matriz, testes e gates verificáveis.
- [x] D1–D6 aprovadas e sincronizadas.
- [x] Aprovação humana para implementação registrada.

**Resultado da DoR:** integralmente atendida; story aprovada humanamente e pronta para desenvolvimento.

## Definition of Done

- [x] AC1–AC6 atendidos com evidências reais e nenhuma persistência de simulação.
- [x] Três casos aprovados e todas as bordas passam; finais exatos, intermediários sob tolerância documentada.
- [x] V5, catálogo, calendário/metadata, portas e erros comprovados em PostgreSQL 16/Testcontainers.
- [x] Spotless, verify, JaCoCo, ArchUnit e regressão frontend passam sem gate relaxado.
- [x] Compose saudável; smoke, logs, métrica, OpenAPI e ausência de escrita comprovados.
- [x] DDL, ER, modelo, contrato, observabilidade, README e AI_USAGE refletem o runtime.
- [x] Gate documental e `git diff --check` passam; revisão não deixa Bloqueantes/Importantes.
- [ ] Aprovação humana final registrada antes de Done.

## Dev Agent Record

### File List

- `backend/pom.xml`; `backend/src/main/resources/db/migration/V5__restrict_currency_minor_units.sql`; `backend/src/main/resources/calendars/anbima-brazil-2025-2030.csv`.
- `backend/src/main/java/com/srm/creditengine/currency/{domain/port,persistence}/` — consulta interna de minor units e adapter PostgreSQL.
- `backend/src/main/java/com/srm/creditengine/pricing/{api,domain,service}/` — contrato HTTP, calendário, potência decimal, cálculo e orquestração.
- `backend/src/test/java/com/srm/creditengine/{CreditEngineApplicationTest,FlywayIntegrationTest,architecture,pricing}/` — testes de unidade, integração, migration, contrato e arquitetura.
- `docs/api/contracts.md`; `docs/database/{ddl.sql,er.md,data-model.md}`; `docs/observability.md`; `README.md`; `AI_USAGE.md`.
- `_bmad-output/implementation-artifacts/e2-s2-simular-em-moeda-do-titulo.md` — execução, evidências e revisão.
- Os arquivos de implementação, testes e documentação acima foram registrados nos commits humanos `e26c0cc`, `a5ab6e2`, `c721f73` e `1939fcd`; a atualização corrente da story permanece sem commit.

### Completion Notes

- Implementados endpoint de simulação sem persistência, ACT/30, calendário ANBIMA local 2025–2030, Strategy por tipo, taxa-base vigente, metadata monetária, potência decimal isolada e erros RFC 9457.
- `big-math` foi fixado em `2.3.2`; intermediários usam `MathContext.DECIMAL128` e somente os resultados monetários finais usam `HALF_EVEN` com os minor units consultados.
- Revisão crítica corrigiu validação positiva no DTO, precedência da validação temporal, metadados/limites do calendário, cobertura do contrato de resposta e guardrails do domínio/HTTP.
- Nenhum endpoint de câmbio, snapshot, settlement, frontend ou persistência de simulação foi introduzido.
- O agente não executou commits. Posteriormente, a autora registrou a implementação em quatro commits atômicos: `e26c0cc`, `a5ab6e2`, `c721f73` e `1939fcd`.

### Evidências

- Aprovação humana das decisões D1–D6 registrada em 2026-09-26.
- Dependência financeira efetivamente resolvida e testada: `ch.obermuhlner:big-math:2.3.2`, isolada atrás de `DecimalPower`.
- Calendário ANBIMA 2025–2030 efetivamente versionado em `backend/src/main/resources/calendars/anbima-brazil-2025-2030.csv`, com fonte, cobertura e data de atualização validadas no carregamento.
- Commits executados posteriormente pela autora, nunca pelo agente: `e26c0cc feat(db): add currency minor-unit metadata`; `a5ab6e2 feat(pricing): simulate present value in title currency`; `c721f73 test(pricing): prove title-currency simulation contracts`; `1939fcd docs(pricing): document title-currency simulation`.
- Backend: `spotless:check` e `verify` aprovados; 22 suítes, 117 testes, 0 falhas/erros/skips; ArchUnit e PostgreSQL 16/Testcontainers verdes; JaCoCo 686/718 linhas (95,54%) e 132/166 branches (79,52%).
- Frontend em cópia limpa devido a lock `EPERM` local em `node_modules`: `npm ci`, lint, typecheck, 14 testes e build aprovados; 0 vulnerabilidades; linhas 100% e branches 87,5%. A imagem Docker também executou `npm ci`/build com sucesso.
- Compose final: PostgreSQL, WireMock, backend e frontend healthy. Smokes: BRL `974.81/25.19`, USD `2391.58/108.42`, prazo zero `1000.00/0.00`; OpenAPI expõe `200/400/404/422/500`; métrica observada com 3 chamadas e zero tags.
- Testes comprovam ausência de escrita, V5/minor units, calendário, erros seguros, domínio sem Spring/JPA/Jackson/big-math e representação decimal por string.
- O primeiro `verify` pós-revisão expôs uma falha transitória no teste preexistente de backoff da E1-S2; a repetição integral passou sem alteração nesse teste. Permanece como limitação operacional conhecida, não como falha funcional da E2-S2.

## Review Record

- Revisão de implementação: concluída em 2026-09-26; nenhum achado Bloqueante ou Importante permanece aberto. Recomendação: pronta para revisão humana/PR, mantendo status Review.
- Aprovação humana da especificação: concedida em 2026-09-26.
- Registro de autoria: os quatro commits da implementação foram executados posteriormente pela autora; nenhuma operação Git mutável foi executada pelo agente.

### Review Triage Log

- **Importante — resolvido:** `faceValue=0` atravessava o DTO sem violação por campo; regex e teste HTTP foram corrigidos.
- **Importante — resolvido:** validação de cobertura/calendário e vencimento ocorria depois de consultas; agora precede metadata, tipo e taxa-base.
- **Importante — resolvido:** guardrails não explicitavam ausência de Spring e `big-math` no domínio nem restringiam controllers por anotação; regras e provas foram ampliadas.
- **Importante — resolvido:** contrato feliz verificava apenas parte da resposta; todos os campos financeiros e strings decimais relevantes passaram a ser exercitados.
- **Sugestão — incorporada:** metadados `source_name`, cobertura e data de atualização do CSV são validados no carregamento; fronteiras inferior/superior também são testadas.
- **Sugestão — não implementada:** derivar dinamicamente todas as tabelas para a prova de zero escrita; a lista explícita cobre integralmente o schema de negócio atual e deverá acompanhar novas tabelas.
- **Sugestão — não implementada:** ampliar combinações HTTP redundantes já cobertas nas camadas unitária, de serviço, migration e integração.

## Change Log

- 2026-09-26 — Story criada em Draft a partir do roadmap e baseline concluída.
- 2026-09-26 — D1–D6 aprovadas humanamente; contratos, precisão, calendário, metadata, casos e testes sincronizados; status alterado para Ready for Dev.
- 2026-09-26 — E2-S2 implementada e validada; revisão crítica corrigida; status alterado para Review, aguardando aprovação humana.
- 2026-09-26 — Autoria humana dos commits `e26c0cc`, `a5ab6e2`, `c721f73` e `1939fcd` registrada; File List, Completion Notes, evidências e Review Record sincronizados, mantendo Review.

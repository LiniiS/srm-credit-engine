---
title: 'E2-S2 — Simular em moeda do título'
type: 'feature'
created: '2026-09-26'
status: 'done'
baseline_commit: '4b42f0b'
route: 'full'
route_source: 'auto'
review: 'thorough'
review_source: 'pinned'
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

**Status:** Done

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
- [x] Aprovação humana final registrada antes de Done.

## Dev Agent Record

### File List

- `backend/pom.xml`; `backend/src/main/resources/db/migration/V5__restrict_currency_minor_units.sql`; `backend/src/main/resources/calendars/anbima-brazil-2025-2030.csv`.
- `backend/src/main/java/com/srm/creditengine/currency/{domain/port,persistence}/` — consulta interna de minor units e adapter PostgreSQL.
- `backend/src/main/java/com/srm/creditengine/pricing/{api,domain,service}/` — contrato HTTP, calendário, potência decimal, cálculo e orquestração.
- `backend/src/test/java/com/srm/creditengine/{CreditEngineApplicationTest,FlywayIntegrationTest,architecture,pricing}/` — testes de unidade, integração, migration, contrato e arquitetura.
- `backend/src/test/java/com/srm/creditengine/pricing/api/PricingSimulationFailureHttpTest.java` — matriz HTTP negativa da simulação e contenção de falhas internas.
- `docs/api/contracts.md`; `docs/database/{ddl.sql,er.md,data-model.md}`; `docs/observability.md`; `README.md`; `AI_USAGE.md`.
- `_bmad-output/implementation-artifacts/e2-s2-simular-em-moeda-do-titulo.md` — execução, evidências e revisão.
- Os arquivos de implementação, testes, documentação e revisão foram integrados à `main` nos commits definitivos `4b42f0b`, `c39a2c7`, `2255973`, `78da775`, `af8bf26`, `e34194c`, `4bf23e6`, `030642c`, `0028779`, `90f8fac` e `1f1c587`.

### Completion Notes

- Implementados endpoint de simulação sem persistência, ACT/30, calendário ANBIMA local 2025–2030, Strategy por tipo, taxa-base vigente, metadata monetária, potência decimal isolada e erros RFC 9457.
- `big-math` foi fixado em `2.3.2`; intermediários usam `MathContext.DECIMAL128` e somente os resultados monetários finais usam `HALF_EVEN` com os minor units consultados.
- Revisão crítica corrigiu validação positiva no DTO, precedência da validação temporal, metadados/limites do calendário, cobertura do contrato de resposta e guardrails do domínio/HTTP.
- Correção da revisão final isolou o tratamento HTTP em pricing, removeu o mapeamento genérico de `IllegalArgumentException` para 400, garantiu `presentValue + discount = faceValue`, completou as fronteiras da V5, ampliou a matriz HTTP negativa e enumerou os códigos públicos no contrato.
- Nenhum endpoint de câmbio, snapshot, settlement, frontend ou persistência de simulação foi introduzido.
- O agente não executou commits nem merge. Posteriormente, a autora realizou todos os commits definitivos e integrou o PR à `main` por Rebase and merge.
- Todos os achados Importantes foram resolvidos; não restam achados Bloqueantes ou Importantes. As Sugestões permanecem deliberadamente não implementadas.

### Evidências

- Aprovação humana das decisões D1–D6 registrada em 2026-09-26.
- Dependência financeira efetivamente resolvida e testada: `ch.obermuhlner:big-math:2.3.2`, isolada atrás de `DecimalPower`.
- Calendário ANBIMA 2025–2030 efetivamente versionado em `backend/src/main/resources/calendars/anbima-brazil-2025-2030.csv`, com fonte, cobertura e data de atualização validadas no carregamento.
- Commits definitivos executados pela autora, nunca pelo agente: `4b42f0b docs(story): prepare E2-S2 title-currency simulation`; `c39a2c7 feat(db): add currency minor-unit metadata`; `2255973 feat(pricing): simulate present value in title currency`; `78da775 test(pricing): prove title-currency simulation contracts`; `af8bf26 docs(pricing): document title-currency simulation`; `e34194c docs(story): record E2-S2 implementation evidence`; `4bf23e6 fix(api): isolate pricing error handling`; `030642c fix(pricing): preserve monetary simulation invariant`; `0028779 test(db): prove currency minor-unit boundaries`; `90f8fac docs(api): enumerate pricing simulation errors`; `1f1c587 docs(story): record E2-S2 review corrections`.
- Jobs remotos `backend`, `frontend` e `repository` aprovados após as correções; PR integrado à `main` pela autora por Rebase and merge.
- Backend após as correções finais: `spotless:check` e `verify` aprovados; 23 suítes, 124 testes, 0 falhas/erros/skips; ArchUnit e PostgreSQL 16/Testcontainers verdes; JaCoCo 717/743 linhas (96,50%) e 141/176 branches (80,11%).
- Frontend em cópia limpa devido a lock `EPERM` local em `node_modules`: `npm ci`, lint, typecheck, 14 testes e build aprovados; 0 vulnerabilidades; linhas 100% e branches 87,5%. A imagem Docker também executou `npm ci`/build com sucesso.
- Compose reconstruído após as correções: PostgreSQL, WireMock, backend e frontend healthy. Smokes: BRL `974.81/25.19`, USD `2391.58/108.42`, prazo zero `1000.00/0.00`; em todos, `presentValue + discount = faceValue`. OpenAPI HTTP 200 expõe `200/400/404/422/500`, todos os erros referenciam `PricingSimulationProblemDetail`; Swagger UI e frontend responderam HTTP 200.
- ProblemDetail real de entrada inválida respondeu `400 application/problem+json`, `VALIDATION_ERROR`, violação por campo e nenhum resultado parcial; testes HTTP adicionais cobrem tipo inativo, Strategy ausente e falhas internas de metadata, taxa-base, potência e argumento inesperado sem detalhes internos.
- A V5 foi exercitada em PostgreSQL real nas fronteiras: `minor_units=0` e `6` aceitos, `-1` e `7` rejeitados, com BRL/USD restaurados e comprovados como `2`.
- Testes comprovam ausência de escrita, V5/minor units, calendário, erros seguros, domínio sem Spring/JPA/Jackson/big-math e representação decimal por string.
- O primeiro `verify` pós-revisão expôs uma falha transitória no teste preexistente de backoff da E1-S2; a repetição integral passou sem alteração nesse teste. Permanece como limitação operacional conhecida, não como falha funcional da E2-S2.

## Review Record

- Revisão crítica da implementação anterior: concluída em 2026-09-26 e registrada como etapa intermediária; a revisão final abaixo substitui sua recomendação.
- Aprovação humana da especificação: concedida em 2026-09-26.
- Registro de autoria: commits e merge foram executados pela autora; nenhuma operação Git mutável foi executada pelo agente.
- Revisão final contra `origin/main`: concluída em 2026-09-26; AC1–AC6 atendidos.
- Checks remotos finais confirmados pela autora: jobs `backend`, `frontend` e `repository` aprovados após as correções.
- Resultado final: 0 Bloqueantes e 0 Importantes remanescentes. Sugestões preservadas como melhorias futuras. Recomendação final: **Aprovada**.
- Gates corretivos aprovados com 124 testes backend, 0 falhas/erros/skips, incluindo ArchUnit, PostgreSQL/Testcontainers e a prova de que `presentValue + discount = faceValue`.
- Aprovação e conclusão humanas registradas; PR integrado à `main` por Rebase and merge e status alterado de Review para Done.
- Limitação da revisão automatizada: as lentes BMAD `edge-case-hunter` e `verification-gap` não conseguiram ler seus prompts renderizados por restrição de acesso; `blind-hunter` e `intent-alignment` concluíram, e as alegações relevantes foram verificadas manualmente contra código, testes, story e ADRs.

### Review Triage Log

- **Importante — resolvido:** `faceValue=0` atravessava o DTO sem violação por campo; regex e teste HTTP foram corrigidos.
- **Importante — resolvido:** validação de cobertura/calendário e vencimento ocorria depois de consultas; agora precede metadata, tipo e taxa-base.
- **Importante — resolvido:** guardrails não explicitavam ausência de Spring e `big-math` no domínio nem restringiam controllers por anotação; regras e provas foram ampliadas.
- **Importante — resolvido:** contrato feliz verificava apenas parte da resposta; todos os campos financeiros e strings decimais relevantes passaram a ser exercitados.
- **Sugestão — incorporada:** metadados `source_name`, cobertura e data de atualização do CSV são validados no carregamento; fronteiras inferior/superior também são testadas.
- **Sugestão — não implementada:** derivar dinamicamente todas as tabelas para a prova de zero escrita; a lista explícita cobre integralmente o schema de negócio atual e deverá acompanhar novas tabelas.
- **Sugestão — não implementada:** ampliar combinações HTTP redundantes já cobertas nas camadas unitária, de serviço, migration e integração.

### Final Review Triage — `origin/main...HEAD`

- **Sugestão — transação de leitura:** metadata, tipo e taxa-base são lidos separadamente. Os catálogos atuais são append-only/sem escrita pública, portanto não há inconsistência reproduzível nesta story; uma fronteira de snapshot consistente pode ser considerada quando houver edição concorrente.
- **Refutado — precedência temporal:** a validação de calendário antes dos catálogos é decisão explícita de T5 e garante que datas econômicas inválidas falhem antes de I/O; não existe precedência diferente aprovada para requisições com múltiplos erros.
- **Importante — resolvido — tratamento HTTP acoplado:** o advice de pricing passou a possuir validação e tradução do fluxo de simulação e ambos os advices foram limitados aos respectivos controllers, eliminando a dependência de pricing no handler de currency.
- **Importante — resolvido — `IllegalArgumentException` genérica:** removido o mapeamento global para 400; violações conhecidas continuam explícitas e defeitos inesperados produzem 500 seguro `PRICING_CALCULATION_FAILED`, com prova HTTP negativa.
- **Importante — resolvido — identidade monetária:** o valor presente é arredondado uma única vez e o deságio é derivado de `faceValue - presentValue` na escala monetária; BRL, USD, prazo zero e fronteira `HALF_EVEN` comprovam a identidade exata.
- **Refutado — escala de entrada por minor units:** a story congelada limita explicitamente `faceValue` a duas casas; suportar moeda com valor nominal de seis casas não pertence à E2-S2.
- **Sugestão — representação do nominal:** a resposta preserva a escala textual recebida (`1000`, `1000.0` ou `1000.00`). O contrato exige string decimal, mas não uma escala canônica; padronização pode ser futura.
- **Refutado — invariante de metadata:** `CurrencyMetadata` valida explicitamente `minorUnits` entre 0 e 6 no construtor, além da constraint PostgreSQL.
- **Sugestão — integridade do CSV:** o carregamento valida fonte, cobertura e data, mas não ordenação, duplicatas e pertencimento de cada data ao intervalo. O recurso é imutável/versionado e as datas de negócio exercitadas passam; validação estrutural adicional reduziria risco de manutenção.
- **Sugestão — completude ANBIMA:** os testes amostram fins de semana, Carnaval, Corpus Christi, virada e limites, mas não comparam todos os anos contra um manifesto autoritativo; preservar a conferência integral como melhoria documental/testável.
- **Refutado — nome da constraint V5:** V2 é a única fonte do schema e o PostgreSQL determina `currency_minor_units_check` para o `CHECK` inline; Testcontainers executa V1–V5 do zero com sucesso.
- **Importante — resolvido — falso verde da V5:** Testcontainers/PostgreSQL comprova aceitação de `0`/`6`, rejeição de `-1`/`7` e BRL/USD=`2`.
- **Importante — resolvido — matriz HTTP incompleta:** testes pela fronteira MVC cobrem tipo inativo, Strategy ausente, falhas internas de metadata/taxa/potência e `IllegalArgumentException` inesperada, verificando status, media type, código seguro e ausência de resultado parcial.
- **Sugestão — OpenAPI:** o teste comprova rota, status e presença geral dos schemas, mas usa busca textual global para propriedades; asserts direcionados aos `$ref`, media types, required e formatos evitariam falsos verdes.
- **Sugestão — ausência de persistência:** as contagens cobrem todas as quatro tabelas de negócio atuais e o código não possui porta de escrita no fluxo. A prova deverá ser ampliada quando novas tabelas de simulação/auditoria surgirem.
- **Importante — resolvido — contrato documental incompleto:** `docs/api/contracts.md` enumera todos os códigos estáveis, status e condições da simulação, alinhados ao OpenAPI e ao runtime.
- **Sugestão — logs:** a baixa cardinalidade é visível no código (`outcome`, moeda e tipo no sucesso; código na falha), mas ainda não há captura automatizada que impeça inclusão futura de payload/valores.
- **Refutado — evidência remota:** a autora confirmou jobs `backend`, `frontend` e `repository` verdes; a story também registra comandos, contagens, cobertura, smokes e a limitação operacional, sem atribuir execução ao agente nesta revisão.
- **Sugestão — reutilização posterior:** o caso de uso existe e a integração feliz é provada ponta a ponta, mas a maior parte da evidência passa pela API HTTP; uma futura story consumidora deverá formalizar a porta pública reutilizável sem furar os limites modulares.

## Change Log

- 2026-09-26 — Story criada em Draft a partir do roadmap e baseline concluída.
- 2026-09-26 — D1–D6 aprovadas humanamente; contratos, precisão, calendário, metadata, casos e testes sincronizados; status alterado para Ready for Dev.
- 2026-09-26 — E2-S2 implementada e validada; revisão crítica corrigida; status alterado para Review, aguardando aprovação humana.
- 2026-09-26 — Autoria humana dos commits de implementação definitivos `c39a2c7`, `2255973`, `78da775` e `af8bf26` registrada; File List, Completion Notes, evidências e Review Record sincronizados, mantendo Review.
- 2026-09-26 — Seis achados Importantes da revisão final corrigidos e revalidados; nenhuma Sugestão opcional implementada; recomendação alterada para Aprovar para merge, mantendo Review.
- 2026-09-26 — Commits corretivos humanos definitivos `4bf23e6`, `030642c`, `0028779` e `90f8fac` registrados; gates com 124 testes e identidade monetária confirmados; nenhum Bloqueante ou Importante remanescente; Review preservado.
- 2026-09-26 — Jobs `backend`, `frontend` e `repository` aprovados após as correções; PR integrado à `main` por Rebase and merge pela autora; AC1–AC6 aceitos; sugestões mantidas como melhorias futuras; status alterado para Done.

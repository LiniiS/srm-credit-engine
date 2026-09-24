---
title: 'E0-S2 — Impor limites arquiteturais e CI'
type: 'feature'
created: '2026-09-23'
status: 'in-review'
baseline_commit: '6936f0e3d0879560e997b145caf15a1d0a0dd70d'
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
  - '{project-root}/_bmad-output/implementation-artifacts/epic-0-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/e0-s1-subir-esqueleto-ponta-a-ponta.md'
---

# Story E0-S2 — Impor limites arquiteturais e CI

- **Épico:** E0 — Fundação executável e guardrails
- **Status:** Review
- **Predecessora:** E0-S1 concluída

<frozen-after-approval reason="objetivo, critérios e limites aprovados pela autora; alteração exige renegociação">

## Objetivo e valor

Transformar as decisões arquiteturais e os gates locais da fundação em guardrails automáticos. Um import proibido deve falhar cedo e todo pull request deve validar backend, frontend, Compose e documentação, reduzindo regressões antes das stories de negócio.

## Requisitos e rastreabilidade

| Fonte | Cobertura desta story |
|---|---|
| Épico E0 / E0-S2 | ArchUnit, limites de imports no frontend, CI e acessibilidade automatizada básica. |
| CAP-06, CAP-07 | Qualidade da experiência web e operabilidade por automação. |
| RNF-09, RNF-10, RNF-11, RNF-13 | Compose válido, gates reproduzíveis, dependências verificadas e axe/Testing Library. |
| ARQ-02, ARQ-03 | Monólito modular e `api → service → domain ← persistence`, com reporting em duas camadas. |
| ARQ-11, ARQ-12 | Frontend por features e WCAG 2.2 AA como requisito arquitetural. |
| ARQ-13 | Pull requests com gates no GitHub Flow. |
| ADR-0001 | Regras de módulos, camadas, ciclos e APIs públicas verificadas por ArchUnit. |
| ADR-0002 | `verify` continua usando PostgreSQL 16/Testcontainers, sem H2. |
| ADR-0008 | `app`, `features` e `shared`, boundaries, TypeScript strict, Testing Library e axe. |
| ADR-0009 | CI de PR; Git mutável, merge, tags e histórico continuam humanos. |

Os ADRs 0003–0007 também permanecem normativos, mas E0-S2 não cria cálculo, câmbio, liquidação, contratos HTTP, resiliência ou observabilidade para exercê-los.

## Critérios de aceite

### AC1 — Limites backend são executáveis

**Dado** o pacote `com.srm.creditengine`, **quando** `mvn verify` executar, **então** ArchUnit verifica ausência de ciclos, matriz permitida entre `currency`, `pricing`, `settlement`, `reporting` e `shared`, limites `api/service/domain/persistence`, domínio sem Spring Web/JPA/Jackson e acesso externo somente por APIs/ports públicos.

### AC2 — O guardrail backend prova que detecta violações

**Dadas** fixtures exclusivamente de teste com dependência proibida, **quando** o teste das regras arquiteturais executar, **então** a violação é detectada; a base real permanece verde mesmo sem módulos funcionais, sem depender apenas de regras vazias.

### AC3 — Limites frontend falham no lint

**Dado** um import de `shared` para `app` ou `features`, ou de uma feature para internals de outra, **quando** a configuração ESLint for exercitada, **então** o import é rejeitado; **dado** `app` compondo `shared`/features e uma feature usando `shared`, **então** o import é aceito.

### AC4 — Acessibilidade automatizada da fundação

**Dada** a tela técnica da E0-S1 nos estados carregando e resolvido, **quando** os testes executarem, **então** Testing Library consulta a interface por semântica/nome acessível e axe não encontra violações automatizáveis.

### AC5 — CI valida todo pull request

**Dado** um pull request, **quando** a CI executar em ambiente limpo, **então** jobs independentes validam Java 21/Maven Wrapper (`spotless:check`, `verify`), Node compatível/npm (`npm ci`, lint, typecheck, testes, build), `docker compose config` e o gate documental de story, com permissões mínimas e sem segredos.

### AC6 — Escopo da fundação permanece intacto

**Dada** a implementação concluída, **quando** o diff for revisado, **então** não existem módulos/features vazios, schema ou funcionalidade de negócio, endpoints `/api/v1`, autenticação, H2, Supabase, Kafka, Redis, microserviços, deploy/CD ou observabilidade antecipada.

</frozen-after-approval>

## Tarefas técnicas ordenadas

- [x] **T1 — Backend:** adicionar ArchUnit test-scoped em `backend/pom.xml`; criar regras reutilizáveis e testes em `backend/src/test/java/com/srm/creditengine/architecture/`, incluindo fixtures somente de teste que comprovem rejeição, sem criar pacotes produtivos vazios.
- [x] **T2 — Frontend boundaries:** adicionar dependências fixadas em `frontend/package.json`/`package-lock.json`; configurar `eslint-plugin-boundaries` e `no-restricted-imports` em `frontend/eslint.config.js`; criar teste determinístico da configuração sob `frontend/src/test/architecture/` sem adicionar `features/*` artificiais.
- [x] **T3 — Frontend a11y:** integrar `axe-core` por helper tipado em `frontend/src/test/accessibility.ts` e ampliar `frontend/src/app/App.test.tsx` para os estados existentes, preservando MSW e consultas por role/nome.
- [x] **T4 — CI:** criar `.github/workflows/ci.yml` para `pull_request` e `push` em `main`, com concorrência cancelável, `permissions: contents: read`, caches nativos, instalação reproduzível e jobs de backend, frontend e validações de repositório.
- [x] **T5 — Documentação:** atualizar somente as seções afetadas de `README.md` e `AI_USAGE.md`; registrar na story decisões, resultados reais, File List e evidências. Não alterar ADRs aceitos nem diagramas, pois a arquitetura não muda.
- [x] **T6 — Verificação:** executar todos os gates abaixo, testar deliberadamente os guardrails com fixtures controladas e revisar o diff contra AC6 antes de mover a story para `Review`.

## Limites explícitos de escopo

**Incluído:** ArchUnit e suas provas negativas; boundaries frontend e prova negativa; axe na tela existente; GitHub Actions para gates; documentação estritamente afetada.

**Excluído:** módulos `currency/pricing/settlement/reporting`, features de UI, migrations/tabelas de negócio, API/OpenAPI de negócio, hooks/commitlint, publicação de imagens, deploy, releases/tags, scanning de segurança, Prometheus/Grafana e qualquer comportamento dos épicos E1–E6.

## Arquivos previstos

| Arquivo/padrão | Ação prevista |
|---|---|
| `backend/pom.xml` | Dependência ArchUnit somente para testes. |
| `backend/src/test/java/com/srm/creditengine/architecture/**` | Regras, enforcement e fixtures/testes negativos. |
| `frontend/package.json`, `frontend/package-lock.json` | Boundaries e axe com versões fixadas. |
| `frontend/eslint.config.js` | Matriz de imports e overrides de testes. |
| `frontend/src/test/architecture/**` | Prova válida/inválida da configuração de lint. |
| `frontend/src/test/accessibility.ts`, `frontend/src/app/App.test.tsx` | Helper axe e cenários semânticos. |
| `.github/workflows/ci.yml` | Pipeline de PR/main sem deploy. |
| `README.md`, `AI_USAGE.md`, esta story | CI, guardrails e evidências reais. |

## Estratégia de testes e gates

### Backend

```bash
cd backend
./mvnw -q spotless:check
./mvnw -q verify
```

O `verify` deve executar enforcement real e testes negativos das regras; Testcontainers continua com PostgreSQL 16.

### Frontend

```bash
cd frontend
npm ci
npm run lint
npm run typecheck
npm run test -- --run
npm run build
```

Os testes cobrem imports permitidos/proibidos e axe nos estados existentes, sem esperar tempo real.

### Integração

```bash
docker compose config
docker compose up --build -d
docker compose ps
```

Confirmar os três serviços healthy, readiness da API e frontend acessível; a CI exige `config`, enquanto o smoke completo é evidência local da story.

### Documentação

```bash
bash .agents/skills/srm-documentacao/scripts/check-docs.sh . story
git diff --check
```

## Riscos, dependências e pré-condições

- **Pré-condições:** E0-S1 `Done`; Java 21, Node compatível, npm e Docker disponíveis; ADRs 0001–0009 aceitos.
- **Falso verde por pacotes ausentes:** permitir seletores vazios somente onde inevitável e testar as mesmas regras contra fixtures violadoras.
- **Regra mais restritiva que o ADR:** codificar a matriz aceita e ports públicos, sem proibir dependências legítimas do domínio.
- **ESLint não classificar arquivos/imports:** falhar testes da própria configuração para caminhos válidos e inválidos.
- **axe interpretado como prova WCAG completa:** registrar que não substitui teclado, leitor de tela, contraste ou testes dos fluxos futuros.
- **CI divergente do local:** usar wrappers/lockfile e os mesmos comandos documentados; nenhuma credencial deve ser necessária.
- **Evidência remota da CI:** o agente valida configuração e comandos localmente, mas push e abertura/atualização do pull request são ações humanas; AC5 só recebe evidência final após uma execução real no provedor.

## Definition of Ready

- [x] Objetivo único, valor e predecessora definidos.
- [x] ACs Given/When/Then ligados a requisitos, arquitetura e ADRs.
- [x] Arquivos, ordem, testes, riscos e limites identificados.
- [x] Nenhum contrato financeiro, schema ou decisão arquitetural nova pendente.
- [x] E0-S1 está concluída e fornece baseline executável.
- [x] Aprovação humana desta story e mudança de status para `ready-for-dev`.

**Resultado:** Definition of Ready atendida após aprovação humana.

## Definition of Done

- [x] AC1–AC6 atendidos com evidências registradas.
- [x] Guardrails positivos e negativos passam sem produção artificial.
- [x] CI executa em PR com permissões mínimas e todos os jobs verdes.
- [x] Gates backend, frontend, integração e documentação passam com resultados reais.
- [x] Nenhuma funcionalidade de negócio ou ADR aceito foi alterado.
- [x] README, AI_USAGE, File List, Completion Notes e Change Log refletem o realizado.
- [x] Revisão não mantém achado Bloqueante ou Importante aberto.
- [x] Plano de commits atômicos preparado; commits executados somente pela autora.

## Campos BMAD para implementação, revisão e evidências

### Dev Agent Record

- **Agente/modelo:** Codex (GPT-5), workflow `bmad-build` e skills SRM obrigatórias.
- **Baseline/branch observada:** `feature/e0-s2-architecture-guardrails`, commit `6936f0e3d0879560e997b145caf15a1d0a0dd70d`.
- **Plano de implementação:** T1/AC1–AC2 ArchUnit; T2/AC3 boundaries; T3/AC4 axe; T4/AC5 workflow; T5/AC5–AC6 documentação; T6/AC1–AC6 gates e revisão.
- **Decisões locais e justificativas:** regras ArchUnit reutilizadas tanto na base real quanto em fixtures negativas; fixtures de frontend exercitam a configuração ESLint programaticamente; `color-contrast` é desabilitada no helper axe por limitação de canvas do jsdom, sem desabilitar regras estruturais; CI usa somente wrappers, lockfile e actions oficiais, sem segredos.
- **Completion Notes:** guardrails backend/frontend, cobertura automatizada de acessibilidade e workflow de CI implementados; ambiente integrado saudável; os quatro achados Importantes da revisão final foram corrigidos com classificação explícita do pacote raiz, fixtures isoladas de módulo/camadas/reporting e execução da configuração ESLint real pela API oficial.
- **Riscos/dívidas remanescentes:** axe não substitui validação manual WCAG. Os checks remotos anteriores estavam verdes; após o commit/push humano das correções, o PR deve executar novamente os três jobs antes do merge.

### Evidências por critério

| AC | Evidência automatizada/manual | Resultado |
|---|---|---|
| AC1 | `mvnw.cmd -q verify`: somente `CreditEngineApplication` é permitida no pacote raiz; módulo desconhecido, todas as direções de camada, ciclos, internals, frameworks e exceção de reporting têm provas específicas. | Atendido |
| AC2 | `ArchitectureRulesTest` usa fixtures isoladas e mensagens específicas; a dependência conhecida → desconhecida não depende da importação da classe `legacy`; 16 testes backend passaram. | Atendido |
| AC3 | `eslint-boundaries.test.js` usa a API `ESLint` e o `export default` real sobre fixtures temporárias em paths produtivos; prova imports permitidos/proibidos e arquivo não classificado, com cleanup confirmado. | Atendido |
| AC4 | `App.test.tsx`: axe e consultas semânticas nos estados carregando e resolvido. | Atendido |
| AC5 | PR aberto; execução real do GitHub Actions confirmada pela autora com os jobs `backend`, `frontend` e `repository` verdes. O workflow usa Java 21/Maven Wrapper, Node/npm com `npm ci`, gates frontend, `docker compose config` e gate documental, sem segredos e com `contents: read`. | Atendido — checks remotos aprovados |
| AC6 | Revisão do diff e buscas por itens vedados; somente guardrails, testes, CI e documentação foram adicionados. | Atendido |

### File List

- `.gitattributes`
- `.github/workflows/ci.yml`
- `backend/mvnw` (modo executável para runner Linux)
- `backend/pom.xml`
- `backend/src/test/java/com/srm/creditengine/architecture/ArchitectureRules.java`
- `backend/src/test/java/com/srm/creditengine/architecture/ArchitectureRulesTest.java`
- `backend/src/test/java/com/srm/creditengine/architecture/ArchitectureTest.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixtures/pricing/api/PricingApi.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixtures/shared/domain/ForbiddenSharedDependency.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturescycle/currency/api/CurrencyApi.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturescycle/pricing/api/PricingApi.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturesframework/pricing/domain/FrameworkDependentDomain.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturesinternal/currency/service/CurrencyInternal.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturesinternal/pricing/service/InternalConsumer.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixtureslayers/pricing/api/InvalidApi.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixtureslayers/pricing/domain/DomainType.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturesunknown/legacy/api/LegacyApi.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturesunknown/pricing/service/UnknownModuleConsumer.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturesapi/pricing/api/InvalidApi.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturesapi/pricing/persistence/PersistenceType.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturesbootstrap/CreditEngineApplication.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturesbootstrap/pricing/persistence/InternalType.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturesdomain/pricing/api/ApiType.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturesdomain/pricing/domain/InvalidDomain.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturesdomain/pricing/persistence/PersistenceType.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturesdomain/pricing/service/ServiceType.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturespersistence/pricing/api/ApiType.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturespersistence/pricing/persistence/InvalidPersistence.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturespersistence/pricing/service/ServiceType.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturesreporting/reporting/api/ReportingApi.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturesreporting/reporting/persistence/ReportingQuery.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturesroot/RogueRootClass.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturesservice/pricing/api/ApiType.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturesservice/pricing/persistence/PersistenceType.java`
- `backend/src/test/java/com/srm/creditengine/architecturefixturesservice/pricing/service/InvalidService.java`
- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/eslint.config.js`
- `frontend/src/app/App.test.tsx`
- `frontend/src/test/accessibility.ts`
- `frontend/src/test/architecture/eslint-boundaries.test.js`
- `README.md`
- `AI_USAGE.md`
- `_bmad-output/implementation-artifacts/e0-s2-impor-limites-arquiteturais-e-ci.md`

### Testes e gates executados

| Data | Comando/verificação | Resultado | Evidência |
|---|---|---|---|
| 2026-09-23 | `mvnw.cmd -q spotless:check` e `mvnw.cmd -q verify` com Java 21 | Aprovado | 10 testes, 0 falhas/erros/ignorados; Flyway/Testcontainers com PostgreSQL 16 |
| 2026-09-23 | `npm ci`, lint, typecheck, `npm run test -- --run`, build | Aprovado | 3 arquivos/13 testes; cobertura 100% statements, 87,5% branches, 100% functions/lines; build Vite concluído |
| 2026-09-23 | Provas negativas ArchUnit e ESLint | Aprovado | fixtures inválidas foram detectadas; imports permitidos permaneceram aceitos |
| 2026-09-23 | `docker compose config` e `docker compose up --build -d` | Aprovado | imagens reconstruídas e configuração válida |
| 2026-09-23 | `docker compose ps`, readiness, frontend e `pg_isready` | Aprovado | três serviços healthy; API 200/UP, frontend 200, PostgreSQL aceitando conexões, CORS para a origem da SPA |
| 2026-09-23 | Parse local de `.github/workflows/ci.yml` e revisão dos jobs | Aprovado localmente | YAML válido; jobs `backend`, `frontend` e `repository`; execução remota confirmada posteriormente |
| 2026-09-23 | GitHub Actions no PR | Aprovado remotamente | confirmação humana: jobs `backend`, `frontend` e `repository` verdes |
| 2026-09-23 | Revisão final local: `spotless:check`, `verify`, `npm ci`, lint, typecheck, testes, build, Compose, documentação e diff | Aprovado nos comandos | backend verde; frontend 3 arquivos/13 testes, cobertura 100% statements/lines/functions e 87,5% branches; npm audit 0 vulnerabilidades; docs 0 erros/3 avisos de release |
| 2026-09-23 | Correções da revisão: `spotless:check` e `verify` | Aprovado | 16 testes backend, 0 falhas/erros/ignorados; fixtures específicas de raiz/bootstrap, módulo, camadas e reporting |
| 2026-09-23 | Correções da revisão: lint, typecheck, testes e build frontend | Aprovado | 3 arquivos/14 testes; configuração ESLint real e overrides de aliases exercitados; cobertura 100% statements/lines/functions e 87,5% branches |
| 2026-09-23 | Correções da revisão: `docker compose config` | Aprovado | configuração Compose válida; nenhuma fixture temporária permaneceu |
| 2026-09-23 | `check-docs.sh . story` | Aprovado | 0 erros; 3 avisos esperados para documentos obrigatórios apenas no release |
| 2026-09-23 | `git diff --check` e busca de itens vedados | Aprovado | sem erros de whitespace; nenhum item fora do escopo encontrado |

### Review Record

- **Revisor/agente:** Codex, revisão final BMAD com lentes blind, edge-case, verification-gap e intent-alignment.
- **Base de comparação:** `origin/main...feature/e0-s2-architecture-guardrails`, incluindo os seis commits da branch e todos os arquivos do PR.
- **Achados Bloqueantes:** nenhum.
- **Achados Importantes:** todos resolvidos: pacote raiz fechado com allowlist do bootstrap; módulo desconhecido isolado; direções de camada e reporting cobertos; configuração ESLint efetiva exercitada pela API oficial.
- **Sugestões:** testar dependências permitidas e a exceção de reporting; avaliar pinagem das actions por SHA e `timeout-minutes`; remover ou configurar aliases atualmente usados apenas nas provas de `no-restricted-imports`.
- **Checks remotos:** confirmação humana de `backend`, `frontend` e `repository` verdes no PR; AC5 encerrado.
- **Limitações remanescentes:** axe não cobre contraste no jsdom nem substitui teclado/leitor de tela; a CI do PR precisa rodar novamente após o push humano das correções.
- **Recomendação:** **Aprovar após nova CI verde no PR**; manter em `Review` até a confirmação humana.
- **Aprovação humana:** PR e checks remotos confirmados pela autora; aprovação final da story ainda pendente após correções.

## Review Triage Log

| Lente/achado | Veredito | Evidência e rota |
|---|---|---|
| Blind 1 — alvo em módulo raiz desconhecido era ignorado | medium | Confirmado no ramo `targetModule == null`; corrigido para rejeitar tipos internos ao root fora da lista aprovada. |
| Blind 2 — `reporting.api → persistence` era proibido | medium | Confirmado contra o fluxo de duas camadas aceito; corrigido com exceção exclusiva para `reporting.api`. |
| Blind 3 — `api → domain` direto era permitido | medium | Confirmado contra `api → service → domain`; regra explícita adicionada. |
| Blind 4 — somente uma família ArchUnit tinha prova negativa | medium | Confirmado porque a produção ainda não possui essas camadas; fixtures de ciclo, framework, camada, internal e módulo desconhecido adicionadas. |
| Blind 5 — teste recriava `boundarySettings` | medium | Confirmado; factory compartilhada passou a alimentar configuração e fixtures. |
| Blind 6 — arquivos/imports desconhecidos escapavam | medium | Confirmado; `no-unknown` e `no-unknown-files` habilitados em produção, com exceção explícita para infraestrutura de teste/configuração. |
| Blind 7 — `no-restricted-imports` por alias não era exercitado | low | O boundaries já cobria imports relativos, mas a defesa solicitada não tinha prova; regras exportadas e dois cenários negativos adicionados. |
| Blind 8 — axe no container não cobre regras de documento | false | O AC4 exige a tela técnica renderizada, que está integralmente no container; idioma do documento pertence ao HTML estático e não é alterado pelos estados do componente. |
| Blind 9 — actions por tags e ausência de timeout | low | Melhoria de hardening válida, mas scanning/supply-chain adicional está fora do escopo explícito e não impede os gates definidos; rejeitado nesta story. |
| Blind 10 — T6 concluída antes dos gates documentais finais | low | Estado transitório do próprio processo; resolvido pela execução e registro dos gates após a atualização final. |
| Edge 1 — arquivos fora de elementos poderiam escapar | medium | Mesmo defeito do Blind 6, verificado e corrigido com regras `no-unknown*`. |
| Edge 2 — API poderia importar domínio diretamente | medium | Mesmo defeito do Blind 3, verificado e corrigido. |
| Verification 1 — teste não protegia settings/overrides reais | medium | Confirmado; factory e regras restritas são compartilhadas e exercitadas por cinco cenários boundaries e dois aliases restritos. |
| Verification 2 — famílias ArchUnit sem prova negativa | medium | Confirmado e corrigido com cinco fixtures adicionais; `verify` executou 10 testes sem falhas. |
| Intent — enforcement futuro demonstrado principalmente em fixtures | false | É a estratégia exigida para não criar produção vazia; as mesmas regras também são aplicadas ao código produtivo. |
| Intent — CI ainda sem evidência remota | maybe-false | Configuração e comandos locais estão verificados; somente um run no PR humano pode concluir AC5, mantido parcial. |
| Intent — Compose em CI valida apenas `config` | false | Coincide exatamente com o comando exigido pelo AC5; smoke completo foi executado localmente. |
| Intent — axe não cobre estado indisponível/contraste | false | AC4 congela apenas carregando e resolvido; contraste automatizado depende de canvas indisponível no jsdom e está documentado. |

### Triage da revisão final contra `origin/main`

| Lente/achado | Veredito | Evidência e rota |
|---|---|---|
| Blind/Edge/Intent — classes no pacote raiz escapam da matriz | medium | Confirmado: `moduleOf` retorna `null` para o pacote raiz e a condição só rejeita subpacotes desconhecidos. Achado Importante; corrigir código antes do merge. |
| Blind — fixture unknown pode passar sem provar a aresta conhecida → desconhecida | medium | Confirmado: a própria classe sob `legacy` produz a mensagem esperada antes de isolar a dependência. Achado Importante; tornar a prova específica. |
| Blind — dependências permitidas não possuem fixtures positivas | low | Confirmado; sugestão para evitar regra excessivamente restritiva quando os módulos reais surgirem. |
| Blind/Verification — exceção `reporting.api → persistence` e direções de camada sem provas específicas | medium | Confirmado no inventário de fixtures. Achado Importante por risco de regressão silenciosa em regra aceita pelo ADR-0001. |
| Blind — todo tipo público em `module.api` é contrato intermodular | false | O ADR-0001 e a story definem o pacote `api` como superfície pública; distinção adicional exigiria nova convenção não aprovada. |
| Blind — `app` pode importar internals de feature | false | O ADR-0008 proíbe internals entre features e define `app` como composição; não exige entry point exclusivo para `app`. |
| Blind — aliases das regras restritas não estão configurados | low | Confirmado, mas boundaries cobre imports relativos reais; sugestão para remover redundância ou configurar aliases quando adotados. |
| Blind/Verification — teste frontend não executa a configuração flat efetiva | medium | Confirmado: o teste importa peças e monta outro objeto; desconectar regras do `export default` não o faz falhar. Achado Importante para AC3. |
| Blind — axe usa apenas o container React | false | Carried: AC4 exige os estados da tela renderizada; regras document-level pertencem ao HTML estático e contraste está explicitamente fora do jsdom. |
| Blind — actions referenciadas por tags mutáveis | low | Sugestão de hardening/reprodutibilidade; não viola permissões mínimas, ausência de segredos ou comandos congelados pelo AC5. |
| Blind — jobs sem `timeout-minutes` | low | Sugestão operacional; os checks reais concluíram e o AC5 não exige timeout explícito. |
| Blind — File List omitia `.gitattributes` e `backend/mvnw` | low | Confirmado e corrigido somente na story nesta revisão. |
| Intent — CI carecia de evidência remota | false | Superado pela confirmação humana de que os três jobs reais do PR passaram. |
| Intent — Compose remoto valida apenas configuração | false | O AC5 exige `docker compose config`; build, saúde e smoke permanecem evidência local conforme a story. |
| Intent — fixtures concentram a prova dos limites futuros | false | Estratégia explicitamente exigida para não criar produção vazia; as regras também analisam a base produtiva. |

### Revisão crítica das correções

- **Bloqueantes:** nenhum.
- **Importantes:** nenhum aberto. A revisão intermediária detectou e corrigiu o acesso do bootstrap a internals, a ausência de provas dos overrides por alias e o risco de sobrescrever arquivos preexistentes nas fixtures temporárias.
- **Sugestões:** as sugestões anteriores permanecem fora deste patch, conforme escopo solicitado.
- **Segurança das fixtures:** criação com `flag: 'wx'`, nomes reservados `__architecture_*` e cleanup somente do conjunto efetivamente criado; nenhum arquivo temporário permaneceu após o gate.

### Change Log

| Data | Alteração | Autor |
|---|---|---|
| 2026-09-23 | Story E0-S2 preparada a partir do planejamento, E0-S1 e ADRs aceitos; nenhuma implementação realizada. | Codex |
| 2026-09-23 | Story aprovada pela autora e promovida para `ready-for-dev`; nenhuma implementação realizada. | Autora |
| 2026-09-23 | T1–T6 implementadas e validadas localmente; story movida para `Review`, com AC5 aguardando evidência da CI no PR. | Codex |
| 2026-09-23 | Revisão final contra `origin/main`; checks remotos `backend`, `frontend` e `repository` confirmados verdes e AC5 encerrado. Achados Importantes de falso verde mantêm a recomendação de correção antes do merge. | Codex + confirmação da autora |
| 2026-09-23 | Quatro achados Importantes corrigidos exclusivamente nos guardrails e fixtures de teste; gates locais completos aprovados; story mantida em `Review`. | Codex |

### Handoff / próximo passo exato

Executar os commits corretivos e o push manualmente. Confirmar nova execução verde de `backend`, `frontend` e `repository` no PR; manter a story em `Review` até a aprovação humana final.

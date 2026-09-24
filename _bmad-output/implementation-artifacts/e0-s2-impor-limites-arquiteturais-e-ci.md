---
title: 'E0-S2 — Impor limites arquiteturais e CI'
type: 'feature'
created: '2026-09-23'
status: 'in-review'
baseline_commit: '6936f0e3d0879560e997b145caf15a1d0a0dd70d'
route: 'full'
route_source: 'auto'
review: 'thorough'
review_source: 'auto'
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

- [ ] AC1–AC6 atendidos com evidências registradas.
- [x] Guardrails positivos e negativos passam sem produção artificial.
- [ ] CI executa em PR com permissões mínimas e todos os jobs verdes.
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
- **Completion Notes:** guardrails backend/frontend, cobertura automatizada de acessibilidade e workflow de CI implementados; ambiente integrado saudável; revisão crítica corrigiu os falsos verdes de resolução TypeScript, seleção de módulos backend desconhecidos e escopo da análise axe.
- **Riscos/dívidas remanescentes:** AC5 aguarda execução real dos jobs no GitHub após push e abertura do PR pela autora; axe não substitui validação manual WCAG.

### Evidências por critério

| AC | Evidência automatizada/manual | Resultado |
|---|---|---|
| AC1 | `mvnw.cmd -q verify`: regras de módulos, camadas, ciclos, APIs/ports e dependências de domínio sobre a base produtiva. | Atendido localmente |
| AC2 | `ArchitectureRulesTest` aplica as mesmas regras a fixtures inválidas e exige violações específicas; 10 testes backend passaram. | Atendido |
| AC3 | lint e `eslint-boundaries.test.js`: três imports proibidos rejeitados e dois permitidos aceitos. | Atendido |
| AC4 | `App.test.tsx`: axe e consultas semânticas nos estados carregando e resolvido. | Atendido |
| AC5 | Workflow validado por parser YAML e coerência local; todos os comandos reproduzidos localmente. Execução no GitHub requer push/PR humano. | Parcial — evidência remota pendente |
| AC6 | Revisão do diff e buscas por itens vedados; somente guardrails, testes, CI e documentação foram adicionados. | Atendido |

### File List

- `.github/workflows/ci.yml`
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
- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/eslint.config.js`
- `frontend/src/app/App.test.tsx`
- `frontend/src/test/accessibility.ts`
- `frontend/src/test/architecture/eslint-boundaries.test.js`
- `frontend/src/test/architecture/fixtures/app/index.ts`
- `frontend/src/test/architecture/fixtures/app/source.ts`
- `frontend/src/test/architecture/fixtures/features/pricing/index.ts`
- `frontend/src/test/architecture/fixtures/features/pricing/source.ts`
- `frontend/src/test/architecture/fixtures/features/settlements/internal.ts`
- `frontend/src/test/architecture/fixtures/shared/source.ts`
- `frontend/src/test/architecture/fixtures/shared/value.ts`
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
| 2026-09-23 | Parse local de `.github/workflows/ci.yml` e revisão dos jobs | Aprovado localmente | YAML válido; jobs `backend`, `frontend` e `repository`; execução remota pendente |
| 2026-09-23 | `check-docs.sh . story` | Aprovado | 0 erros; 3 avisos esperados para documentos obrigatórios apenas no release |
| 2026-09-23 | `git diff --check` e busca de itens vedados | Aprovado | sem erros de whitespace; nenhum item fora do escopo encontrado |

### Review Record

- **Revisor/agente:** Codex, revisão crítica BMAD do diff completo.
- **Base de comparação:** commit `6936f0e3d0879560e997b145caf15a1d0a0dd70d` e arquivos não rastreados da E0-S2.
- **Achados Bloqueantes:** nenhum aberto.
- **Achados Importantes:** corrigidos: resolvedor TypeScript ausente no teste de boundaries; seletor ArchUnit sem rejeição explícita de módulo raiz desconhecido; axe inicialmente limitado a uma região em vez de toda a tela renderizada.
- **Sugestões:** manter a execução remota da CI como evidência obrigatória antes de concluir a story.
- **Recomendação:** Review; não promover para Done até CI verde no PR e aprovação humana.
- **Aprovação humana:** pendente.

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

### Change Log

| Data | Alteração | Autor |
|---|---|---|
| 2026-09-23 | Story E0-S2 preparada a partir do planejamento, E0-S1 e ADRs aceitos; nenhuma implementação realizada. | Codex |
| 2026-09-23 | Story aprovada pela autora e promovida para `ready-for-dev`; nenhuma implementação realizada. | Autora |
| 2026-09-23 | T1–T6 implementadas e validadas localmente; story movida para `Review`, com AC5 aguardando evidência da CI no PR. | Codex |

### Handoff / próximo passo exato

A autora deve revisar o diff, executar os commits sugeridos, publicar a branch e abrir/atualizar o PR. Após os três jobs da CI ficarem verdes, registrar essa evidência, realizar a aprovação humana e só então avaliar a promoção para `Done`.

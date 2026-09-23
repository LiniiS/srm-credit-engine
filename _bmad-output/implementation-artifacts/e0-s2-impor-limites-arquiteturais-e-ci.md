---
title: 'E0-S2 — Impor limites arquiteturais e CI'
type: 'feature'
created: '2026-09-23'
status: 'ready-for-dev'
route: 'full'
route_source: 'auto'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-0-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/e0-s1-subir-esqueleto-ponta-a-ponta.md'
---

# Story E0-S2 — Impor limites arquiteturais e CI

- **Épico:** E0 — Fundação executável e guardrails
- **Status:** Ready for Dev
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

- [ ] **T1 — Backend:** adicionar ArchUnit test-scoped em `backend/pom.xml`; criar regras reutilizáveis e testes em `backend/src/test/java/com/srm/creditengine/architecture/`, incluindo fixtures somente de teste que comprovem rejeição, sem criar pacotes produtivos vazios.
- [ ] **T2 — Frontend boundaries:** adicionar dependências fixadas em `frontend/package.json`/`package-lock.json`; configurar `eslint-plugin-boundaries` e `no-restricted-imports` em `frontend/eslint.config.js`; criar teste determinístico da configuração sob `frontend/src/test/architecture/` sem adicionar `features/*` artificiais.
- [ ] **T3 — Frontend a11y:** integrar `axe-core` por helper tipado em `frontend/src/test/accessibility.ts` e ampliar `frontend/src/app/App.test.tsx` para os estados existentes, preservando MSW e consultas por role/nome.
- [ ] **T4 — CI:** criar `.github/workflows/ci.yml` para `pull_request` e `push` em `main`, com concorrência cancelável, `permissions: contents: read`, caches nativos, instalação reproduzível e jobs de backend, frontend e validações de repositório.
- [ ] **T5 — Documentação:** atualizar somente as seções afetadas de `README.md` e `AI_USAGE.md`; registrar na story decisões, resultados reais, File List e evidências. Não alterar ADRs aceitos nem diagramas, pois a arquitetura não muda.
- [ ] **T6 — Verificação:** executar todos os gates abaixo, testar deliberadamente os guardrails com fixtures controladas e revisar o diff contra AC6 antes de mover a story para `Review`.

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
- [ ] Guardrails positivos e negativos passam sem produção artificial.
- [ ] CI executa em PR com permissões mínimas e todos os jobs verdes.
- [ ] Gates backend, frontend, integração e documentação passam com resultados reais.
- [ ] Nenhuma funcionalidade de negócio ou ADR aceito foi alterado.
- [ ] README, AI_USAGE, File List, Completion Notes e Change Log refletem o realizado.
- [ ] Revisão não mantém achado Bloqueante ou Importante aberto.
- [ ] Plano de commits atômicos preparado; commits executados somente pela autora.

## Campos BMAD para implementação, revisão e evidências

### Dev Agent Record

- **Agente/modelo:**
- **Baseline/branch observada:**
- **Plano de implementação:**
- **Decisões locais e justificativas:**
- **Completion Notes:**
- **Riscos/dívidas remanescentes:**

### Evidências por critério

| AC | Evidência automatizada/manual | Resultado |
|---|---|---|
| AC1 | | Não executado |
| AC2 | | Não executado |
| AC3 | | Não executado |
| AC4 | | Não executado |
| AC5 | | Não executado |
| AC6 | | Não executado |

### File List

- A preencher durante a implementação.

### Testes e gates executados

| Data | Comando/verificação | Resultado | Evidência |
|---|---|---|---|
| | | Não executado | |

### Review Record

- **Revisor/agente:**
- **Base de comparação:**
- **Achados Bloqueantes:**
- **Achados Importantes:**
- **Sugestões:**
- **Recomendação:**
- **Aprovação humana:**

### Change Log

| Data | Alteração | Autor |
|---|---|---|
| 2026-09-23 | Story E0-S2 preparada a partir do planejamento, E0-S1 e ADRs aceitos; nenhuma implementação realizada. | Codex |
| 2026-09-23 | Story aprovada pela autora e promovida para `ready-for-dev`; nenhuma implementação realizada. | Autora |

### Handoff / próximo passo exato

Após aprovação humana, alterar apenas o status para `ready-for-dev` e implementar T1–T6 em branch curta dedicada, sem operações Git mutáveis por agentes.

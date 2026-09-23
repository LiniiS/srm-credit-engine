---
title: 'E0-S1 — Subir o esqueleto ponta a ponta'
type: 'feature'
created: '2026-09-23'
status: 'done'
baseline_commit: 'f25906884b9fd47bc3487c5af1d8f383e8694866'
route: 'full'
route_source: 'auto'
review: 'thorough'
review_source: 'auto'
lenses_ran: [blind-hunter, edge-case-hunter, verification-gap, intent-alignment]
review_loop_iteration: 0
context:
  - '{project-root}/AGENTS.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-0-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/e0-s1-subir-esqueleto-ponta-a-ponta.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** O repositório ainda não possui um caminho executável que prove, a partir de um checkout limpo, a integração entre PostgreSQL 16, API Spring Boot e SPA React. Sem essa fundação, as stories funcionais não têm ambiente, migração ou gates reproduzíveis.

**Approach:** Entregar o menor corte vertical técnico: backend com readiness e Flyway, frontend acessível que consulta essa readiness, imagens multi-stage não-root e Compose com três healthchecks. Completar com testes, lockfiles, configuração externa e documentação verificável.

## Boundaries & Constraints

**Always:** Java 21/Spring Boot 3, PostgreSQL 16, Flyway como única fonte do schema, Testcontainers PostgreSQL sem H2, React/TypeScript strict/Vite, variáveis de ambiente, lockfiles, Dockerfiles multi-stage e runtime não-root. Reaproveitar `.env.example`, `compose.yaml` e placeholders após analisá-los; preservar os ADRs aceitos e registrar resultados reais na story e em `AI_USAGE.md`.

**Never:** Criar schema ou funcionalidade de negócio, pacotes/features vazios, contratos `/api/v1`, autenticação, Supabase, Kafka, Redis, microserviços, observabilidade completa, CI, ArchUnit ou guardrails de E0-S2. Não executar operações Git mutáveis nem marcar Review com gate obrigatório falhando.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Ambiente saudável | `docker compose up --build` sem `.env` | `postgres`, `backend` e `frontend` healthy; SPA mostra API disponível | Sem erro esperado |
| API indisponível | Falha de rede ou readiness não saudável | SPA mostra indisponibilidade em texto e região semântica | Sem dado fictício ou stack trace |
| Banco vazio | Primeira inicialização do backend | Flyway cria apenas sua migration técnica e registra histórico | Startup falha se migração/banco falhar |
| Configuração sobrescrita | Variáveis de portas, banco ou URL pública definidas | Compose e aplicações usam os valores fornecidos | Defaults locais continuam seguros e coerentes |

</frozen-after-approval>

## Code Map

- `.env.example` -- já contém as sete variáveis necessárias; validar nomes/defaults sem inserir segredos.
- `compose.yaml` -- já define PostgreSQL 16 e dependências por saúde; corrigir frontend healthcheck e alinhar imagens/configuração.
- `README.md`, `package.json`, `pom.xml` -- placeholders vazios; README será preenchido e manifests ambíguos da raiz removidos.
- `backend/` -- diretório vazio destinado ao Maven Wrapper, aplicação Spring, migration técnica, testes e container.
- `frontend/` -- diretório vazio destinado ao Vite, bootstrap `app/`, cliente de health em `shared/`, testes e container.
- `docs/architecture/c4-container.md` -- já nomeia os três containers; atualizar somente o estado implementado.
- `AI_USAGE.md` -- preservar histórico e acrescentar fatos materiais desta implementação.
- `_bmad-output/implementation-artifacts/e0-s1-subir-esqueleto-ponta-a-ponta.md` -- fonte normativa e registro final de tasks, evidências e status.

## Tasks & Acceptance

**Execution:**
- [x] `backend/**` -- criar aplicação Java 21 com Actuator readiness, datasource/Flyway, migration técnica, Spotless, JaCoCo, Maven Wrapper e testes de contexto/readiness/Flyway Testcontainers.
- [x] `frontend/**` -- criar SPA React strict com cliente configurável, estados acessíveis disponível/indisponível, Vitest/Testing Library/MSW, lint, cobertura, lockfile e build.
- [x] `backend/Dockerfile`, `frontend/Dockerfile`, `.dockerignore`, `compose.yaml`, `.env.example` -- construir runtimes mínimos não-root e três healthchecks coerentes.
- [x] `README.md`, `docs/architecture/c4-container.md`, `AI_USAGE.md` -- documentar somente o comportamento comprovado, limitações e uso material de IA.
- [x] Story BMAD -- atualizar apenas checkboxes, status, Dev Agent Record, evidências, File List e Change Log.

**Acceptance Criteria:**
- Given checkout limpo com Docker, when Compose constrói e inicia, then os três serviços ficam healthy sem `.env` obrigatório.
- Given SPA aberta, when a API está saudável ou indisponível, then o estado correto é anunciado textualmente e testado por comportamento.
- Given PostgreSQL vazio, when o backend inicia, then Flyway executa automaticamente e o teste usa PostgreSQL 16 real.
- Given os gates obrigatórios, when executados, then todos passam e os resultados reais ficam registrados.
- Given README seguido do zero, when comandos e URLs são usados, then correspondem ao ambiente implementado e deixam explícita a ausência de negócio.

## Implementation Notes

- Reutilizados e corrigidos `.env.example`, `compose.yaml` e README placeholder após inspeção; manifests vazios da raiz foram removidos.
- Backend: Spring Boot 3.4.2/Java 21, readiness incluindo `db`, migration técnica e PostgreSQL 16.6 Testcontainers.
- Frontend: React 19/Vite 6/TypeScript strict, estados acessíveis, timeout de 5 s e cobertura acima de 80%.
- Containers multi-stage rodam como `creditengine` uid 100 e `nginx` uid 101.
- Validação integrada usou `POSTGRES_PORT=5433` porque 5432 estava ocupada por container externo; nenhum recurso externo foi alterado.
- O staging pedido genericamente pelo workflow foi substituído por diff temporário sem índice, por precedência da proibição Git do projeto e do usuário.

## Spec Change Log

- 2026-09-23: plano aprovado e implementado sem alterar o bloco congelado.

## Review Triage Log

| Origem | Achado | Veredito e evidência | Rota |
|---|---|---|---|
| blind-1 | `BACKEND_PORT` não altera `VITE_API_URL` | false — são configurações públicas independentes e ambas estão documentadas; a story exige sobrescrita, não acoplamento implícito. | rejeitado |
| blind-2 | `FRONTEND_PORT` não altera `FRONTEND_ORIGIN` | false — origem e porta são variáveis independentes documentadas para sobrescrita coordenada. | rejeitado |
| blind-3 | fetch sem timeout | medium — conexão pendurada manteria loading indefinidamente; confirmado em `health.ts`. | patch: timeout de 5 s |
| blind-4 | readiness só no mount | false — a story pede estado inicial disponível/indisponível, não polling ou recuperação contínua. | rejeitado |
| blind-5 | readiness não inclui DB | medium — configuração original incluía apenas readinessState implícito. | patch: grupo inclui `db` |
| blind-6 | teste readiness exclui DB | medium — teste unitário estava isolado; Testcontainers agora também consulta readiness com DB real. | patch |
| blind-7 | testes fixam URL default | false — testes unitários cobrem comportamento; configuração alternativa foi exercida por Compose/config e não exige teste de bundle automatizado. | rejeitado |
| blind-8 | nginx não implementa proxy citado na spec | false — produção usa URL pública absoluta; a nota descrevia opção, não requisito nem comportamento necessário. | rejeitado |
| blind-9 | imagens sem digest | false — versões patch estão fixadas como permitido explicitamente pela story; digest não é requisito. | rejeitado |
| blind-10 | Maven sem SHA-256 | low — melhoria de supply chain válida, mas não necessária ao incremento e requer dado externo; wrapper está versionado e versão fixada. | rejeitado |
| blind-11 | spec sem registros | low — achado observou estado intermediário antes do fechamento; tasks e notas foram preenchidas. | patch |
| blind-12 | story ausente do diff | low — achado observou estado intermediário; story foi atualizada integralmente antes da conclusão. | patch |
| blind-13 | runtime BMAD no diff | false — `_bmad/render` é ignorado e não aparece no status versionável; o diff temporário refletia trabalho de baseline preexistente. | rejeitado |
| blind-14 | AI_USAGE sem comandos exatos | false — comandos/resultados auditáveis pertencem ao Dev Agent Record e foram registrados ali; AI_USAGE registra contribuição material. | rejeitado |
| blind-15 | `.dockerignore` omite tsbuildinfo | low — metadata local poderia entrar no contexto, embora não na imagem final. | patch: `*.tsbuildinfo` |
| edge-1 | conexão pendurada mantém loading | medium — confirmado; mesmo root cause de blind-3. | patch: timeout de 5 s |
| edge-2 | origem fica stale ao mudar só frontend port | false — variáveis são explicitamente independentes e documentadas. | rejeitado |
| edge-3 | URL fica stale ao mudar só backend port | false — variáveis são explicitamente independentes e documentadas. | rejeitado |
| edge-4 | claim de indisponibilidade não cobre stall | medium — confirmado; mesmo root cause de blind-3. | patch: timeout de 5 s |
| verification-1 | CORS pode regredir sem teste | medium — nenhum teste original afirmava o header. | patch: teste com `Origin` e `Access-Control-Allow-Origin` |
| verification-2 | URL alternativa não é testada no bundle | false — smoke do bundle/API real e `docker compose config` verificam o cenário autorizado; E2E automatizado não é requisito da story. | rejeitado |

O auditor de alinhamento confirmou que o diff implementa o corte artefatual e a prova operacional. A leitura adicional de um E2E automatizado versionado não consta do intent aprovado; a prova ponta a ponta foi executada e registrada como gate operacional.

## Design Notes

- Usar Actuator `/actuator/health/readiness` como contrato técnico, sem criar endpoint de domínio.
- A migration inicial deve ser técnica e sem tabelas de negócio; `ddl-auto` não pode criar ou atualizar schema.
- O frontend pode conter apenas `app/` e `shared/`; o fetch fica fora do componente de apresentação.
- Servir o bundle Vite com nginx não-root e proxyar `/actuator/` ao backend permite prova real SPA→API sem expor hostname interno ao navegador; `VITE_API_URL` continua configurável no build.

## Verification

**Commands:**
- `cd backend && ./mvnw -q spotless:check && ./mvnw -q verify` -- formatação, testes, Testcontainers, cobertura e build verdes.
- `cd frontend && npm run lint && npm run typecheck && npm run test -- --run && npm run build` -- lint, strict typing, estados de health, cobertura e bundle verdes.
- `docker compose config && docker compose up --build && docker compose ps` -- configuração válida e três serviços healthy.
- `curl` nos healthchecks e frontend -- PostgreSQL pronto, API ready e SPA servida consultando a API real.
- `bash .agents/skills/srm-documentacao/scripts/check-docs.sh . story` -- zero erros documentais.
- Seguir README em ambiente limpo e encerrar com `docker compose down` -- instruções práticas coerentes.

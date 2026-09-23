# Story E0-S1 — Subir o esqueleto ponta a ponta

- **Épico:** E0 — Fundação executável e guardrails
- **Status:** Review
- **Tipo:** Fundação técnica vertical
- **Prioridade:** Must
- **Data de preparação:** 2026-09-23
- **Responsável pela implementação:** não atribuído

## Objetivo e valor

Estabelecer o menor caminho executável do monorepo para que, a partir de um checkout limpo, o avaliador consiga subir PostgreSQL 16, API Java 21/Spring Boot 3 e SPA React/TypeScript/Vite com Docker Compose, verificar que os três serviços estão saudáveis e observar a SPA consultando a prontidão real da API.

O valor desta story é reduzir o risco de integração e tornar verificáveis, antes das funcionalidades de negócio, o ambiente local, as migrações, a configuração externa e os gates básicos de backend e frontend.

## Contexto

O PRD exige uma aplicação local demonstrável, e o épico E0 começa por um corte ponta a ponta executável. Hoje existem diretórios e arquivos de fundação vazios ou parciais, mas ainda não há aplicação implementada. Esta story cria apenas a infraestrutura mínima de execução e qualidade. Regras de câmbio, precificação, liquidação, relatórios e telas de negócio pertencem às stories posteriores.

### Fonte do escopo

Extraído de `_bmad-output/planning-artifacts/epics.md`:

> **Valor:** o avaliador confirma rapidamente que SPA, API e banco funcionam juntos.
>
> - Dado um checkout limpo com Docker disponível, quando `docker compose up --build` terminar, então PostgreSQL, API e frontend ficam healthy e a SPA alcança o health da API.
> - Dado o backend inicializado, quando Flyway executar, então o schema nasce sem intervenção manual.
> - Dado o código, quando os gates forem executados, então formatação, testes, typecheck e build passam.

## Requisitos e decisões preservadas

- Java 21 e Spring Boot 3 no backend.
- React, TypeScript com `strict: true` e Vite no frontend.
- PostgreSQL 16 como banco relacional local.
- Docker Compose como forma oficial de executar frontend, backend e banco.
- Flyway como única fonte do schema; sem H2.
- Monorepo com `backend/`, `frontend/`, `docs/` e infraestrutura na raiz/`infra/` quando necessária.
- Backend preparado para monólito modular e hexagonal seletiva, sem criar abstrações ou módulos de negócio vazios nesta story.
- Frontend preparado para organização por features, sem antecipar features de negócio.
- Configuração por variáveis de ambiente; `.env.example` contém apenas valores locais fictícios, nunca segredos reais.
- REST/OpenAPI/ProblemDetail, domínio financeiro, observabilidade completa e acessibilidade dos fluxos de negócio continuam regidos pelos ADRs aceitos, mas só são implementados quando o escopo da story os exigir.

## Critérios de aceite

### AC1 — Checkout limpo sobe os três serviços

**Given** um checkout limpo, Docker e Docker Compose disponíveis e nenhuma `.env` obrigatória criada manualmente  
**When** o avaliador executa `docker compose up --build` na raiz  
**Then** as imagens de backend e frontend são construídas  
**And** PostgreSQL 16, API e frontend iniciam sem intervenção manual  
**And** `docker compose ps` apresenta os três serviços como `healthy` dentro dos limites configurados nos healthchecks.

### AC2 — A SPA alcança a prontidão da API

**Given** os serviços do Compose estão saudáveis  
**When** o avaliador abre a SPA pela porta configurada  
**Then** uma página mínima e sem funcionalidade de negócio consulta a prontidão da API pelo endereço configurado  
**And** apresenta de forma textual e semanticamente identificável que a API está disponível  
**And** falha de rede ou resposta não saudável produz estado textual de indisponibilidade, sem cálculo ou dado fictício de negócio.

### AC3 — Flyway inicializa banco vazio

**Given** um volume PostgreSQL novo e vazio  
**When** o backend inicia  
**Then** Flyway executa automaticamente antes do acesso da aplicação ao banco  
**And** registra com sucesso a migration inicial no histórico do Flyway  
**And** nenhuma execução manual de SQL é necessária  
**And** não existe configuração H2 nem mecanismo concorrente de criação/alteração de schema.

### AC4 — Configuração externa e segura

**Given** os arquivos versionáveis da aplicação  
**When** as configurações são inspecionadas  
**Then** conexão PostgreSQL, portas e URL pública da API para a SPA são configuráveis por variáveis de ambiente  
**And** valores locais não sensíveis possuem defaults coerentes entre Compose e aplicações  
**And** `.env.example` documenta todas as variáveis necessárias sem credenciais reais  
**And** nenhum `.env` real, token ou segredo está incorporado em imagem, bundle ou arquivo de configuração versionável.

### AC5 — Dockerfiles são reproduzíveis e executam com privilégio mínimo

**Given** os Dockerfiles de backend e frontend  
**When** as imagens são construídas pelo Compose  
**Then** cada build é reproduzível a partir dos manifests e lockfiles do projeto  
**And** usa build multi-stage ou separação equivalente entre compilação e runtime  
**And** o processo de runtime não executa como `root`  
**And** a imagem final não contém caches, credenciais ou ferramentas de build desnecessárias.

### AC6 — Gates iniciais do backend passam

**Given** o backend fundacional  
**When** são executados `./mvnw -q spotless:check` e `./mvnw -q verify` dentro de `backend/`  
**Then** compilação Java 21, formatação e testes passam  
**And** existe ao menos um teste de contexto/saúde e um teste de integração que comprova a inicialização Flyway em PostgreSQL 16 via Testcontainers  
**And** nenhum teste de persistência usa H2.

### AC7 — Gates iniciais do frontend passam

**Given** o frontend fundacional  
**When** são executados `npm run lint`, `npm run typecheck`, `npm run test -- --run` e `npm run build` dentro de `frontend/`  
**Then** todos passam com TypeScript strict  
**And** o teste da página mínima verifica os estados disponível e indisponível pelo comportamento observável, sem acoplamento a detalhes internos.

### AC8 — Execução local está documentada e conferida

**Given** um avaliador sem contexto prévio  
**When** segue o README a partir de um checkout limpo  
**Then** encontra pré-requisitos, variáveis, comando para subir, URLs, comando para verificar status, gates locais e procedimento de encerramento  
**And** os comandos, portas e nomes de serviços correspondem à configuração real  
**And** limitações atuais deixam claro que nenhuma funcionalidade financeira foi implementada nesta story.

## Tasks e subtasks

- [x] **T1 — Consolidar a estrutura executável do monorepo** (AC1, AC6, AC7)
  - [x] Manter aplicações independentes em `backend/` e `frontend/`, sem transferir código de negócio para manifests da raiz.
  - [x] Definir manifests/lockfiles e wrappers necessários para builds reproduzíveis.
  - [x] Remover ou substituir placeholders vazios somente quando sua finalidade estiver coberta pela estrutura final.

- [x] **T2 — Criar o backend mínimo Java 21/Spring Boot 3** (AC2, AC3, AC6)
  - [x] Configurar build Maven e Maven Wrapper no diretório `backend/`.
  - [x] Criar bootstrap Spring Boot sem endpoints de domínio.
  - [x] Habilitar endpoint de readiness necessário ao Compose e ao smoke test da SPA.
  - [x] Configurar datasource PostgreSQL e Flyway por variáveis de ambiente.
  - [x] Adicionar migration inicial mínima, sem antecipar tabelas de negócio de E1–E4.
  - [x] Adicionar Spotless e testes fundacionais, incluindo Testcontainers PostgreSQL 16 para Flyway.

- [x] **T3 — Criar o frontend mínimo React/TypeScript/Vite** (AC2, AC7)
  - [x] Configurar React, Vite, TypeScript strict, lint, Vitest e Testing Library.
  - [x] Manter bootstrap em `src/app/` e infraestrutura HTTP mínima em `src/shared/`, sem criar features financeiras vazias.
  - [x] Criar página mínima que consulte o health/readiness da API usando `VITE_API_URL`.
  - [x] Representar estados de carregamento, disponibilidade e indisponibilidade com HTML semântico, texto e foco visível.
  - [x] Testar os estados observáveis da integração sem reimplementar lógica de negócio.

- [x] **T4 — Containerizar backend e frontend** (AC1, AC5)
  - [x] Criar Dockerfile multi-stage do backend com runtime Java 21 e usuário não-root.
  - [x] Criar Dockerfile multi-stage do frontend com servidor estático e usuário não-root.
  - [x] Adicionar `.dockerignore` específicos para reduzir contexto e impedir inclusão acidental de artefatos/segredos.
  - [x] Configurar healthcheck real para o frontend, além dos healthchecks de PostgreSQL e API.

- [x] **T5 — Consolidar `compose.yaml` e variáveis** (AC1, AC2, AC3, AC4)
  - [x] Orquestrar PostgreSQL 16, backend e frontend com dependências condicionadas à saúde.
  - [x] Alinhar portas, URL interna do banco e URL pública da API.
  - [x] Garantir que defaults locais funcionem sem `.env` e possam ser sobrescritos.
  - [x] Atualizar `.env.example` com todas e somente as variáveis necessárias, acompanhadas de valores fictícios seguros.

- [x] **T6 — Documentar a execução mínima** (AC8)
  - [x] Atualizar README com pré-requisitos, setup, URLs, healthchecks, gates e encerramento do ambiente.
  - [x] Registrar limitações e deixar explícito que esta story não entrega negócio.
  - [x] Atualizar C4 Container apenas se nomes, portas ou responsabilidades implementados divergirem do documento atual.
  - [x] Registrar em `AI_USAGE.md` apenas contribuições materiais e fatos reais da implementação.

- [x] **T7 — Executar e registrar verificações** (AC1–AC8)
  - [x] Executar gates do backend e frontend.
  - [x] Executar `docker compose config`.
  - [x] Em ambiente limpo, executar `docker compose up --build`, `docker compose ps` e o smoke test SPA→API.
  - [x] Executar o gate documental em fase `story`.
  - [x] Registrar resultados reais, falhas e evidências no Dev Agent Record; não marcar task concluída sem execução.

## Limites de escopo

### Incluído

- Estrutura mínima de monorepo necessária para executar backend e frontend.
- Aplicações mínimas Java/Spring e React/Vite.
- PostgreSQL 16, Flyway inicial, Dockerfiles, Compose e healthchecks.
- Configuração externa e `.env.example` sem segredos.
- Gates locais iniciais e documentação mínima de execução.
- Smoke path SPA→health/readiness da API.

### Excluído

- Endpoints, tabelas, seeds ou telas de câmbio, taxa base, recebíveis, precificação, liquidação e relatórios.
- Fórmula financeira, Strategy, jOOQ code generation e contratos de negócio.
- Autenticação e autorização.
- Prometheus/Grafana e resiliência do provedor de câmbio, salvo configuração estritamente necessária ao health base; o profile observável pertence a E6-S1.
- ArchUnit e enforcement completo de limites entre módulos; pertencem a E0-S2.
- Regras de imports entre features, axe abrangente e guardrails arquiteturais; pertencem a E0-S2 e E5.
- Workflow/pipeline CI; pertence explicitamente a E0-S2.
- Kafka, Redis, microserviços, Supabase, EDA ou infraestrutura de alta escala.
- Commits, branches, tags ou qualquer operação Git mutável pelo agente.

## Dependências

### Predecessoras

- Planejamento BMAD aprovado.
- ADR-0001 a ADR-0009 com status `Aceito`.
- Docker Engine com Docker Compose para a verificação integrada.
- Java 21, Node.js/npm e shell compatível para execução local dos gates fora dos containers.

### Sucessoras desbloqueadas

- E0-S2 — limites arquiteturais e CI.
- E1–E6 — incrementos funcionais e operacionais, todos dependentes de uma base executável.

## Dev Notes

### Arquitetura e limites

- Respeitar ADR-0001: a fundação deve permitir os módulos `currency`, `pricing`, `settlement`, `reporting` e `shared`, mas não criar pacotes/classes vazios apenas para antecipá-los.
- O health técnico não é contrato de negócio `/api/v1`; usar o mecanismo de health/readiness do Spring Boot. Não inventar endpoint financeiro.
- O frontend pode ter apenas `app/` e `shared/` nesta story. `features/*` deve nascer junto de comportamento real.
- Flyway é a única autoridade do schema. Desabilitar criação automática de schema pelo ORM; a migration inicial não deve antecipar o modelo de stories futuras.
- O teste de Flyway deve usar PostgreSQL 16 via Testcontainers. Um teste apenas com contexto mock não comprova AC3.
- A SPA consome uma URL pública configurável. Valores `VITE_*` são públicos e não podem conter segredos.
- Healthchecks devem testar o processo real, não apenas a existência do container. `depends_on` ordena prontidão, mas não substitui healthcheck.
- O frontend deve permanecer minimamente acessível: landmarks/headings semânticos, texto para estados, foco visível e nenhum estado comunicado apenas por cor. A cobertura WCAG integral permanece bloqueante nas stories de UI.

### Decisões de implementação permitidas

- Selecionar versões patch atuais e compatíveis dentro de Java 21, Spring Boot 3, React, TypeScript e Vite; registrar versões efetivas no README e lockfiles.
- Selecionar imagens runtime compatíveis, fixando tags reproduzíveis e mantendo usuários não-root.
- Escolher a forma mínima de servir os assets do Vite no container, sem introduzir plataforma ou framework adicional de aplicação.

### Restrições operacionais

- Defaults do Compose servem somente ao ambiente local. Produção não pode herdar senha fictícia.
- Não registrar resultados de `docker compose up`, testes ou smoke test sem execução real.
- Se a implementação exigir dependência estrutural não prevista, mudança nos limites ou schema de negócio, interromper e aplicar `srm-arquitetura` antes de prosseguir.

## Referências aos ADRs

| ADR | Aplicação nesta story |
|---|---|
| ADR-0001 | Monorepo, monólito modular e hexagonal seletiva sem abstrações vazias. |
| ADR-0002 | PostgreSQL 16, Flyway como fonte única e Testcontainers sem H2. |
| ADR-0003 | Guardrail: não introduzir matemática financeira nesta fundação. |
| ADR-0004 | Guardrail: não antecipar integração ou regra cambial. |
| ADR-0005 | Guardrail: não antecipar schema/fluxo de liquidação. |
| ADR-0006 | Separar health técnico de contratos REST de negócio. |
| ADR-0007 | Health base agora; logs/métricas completos e profile observável permanecem em E6-S1. |
| ADR-0008 | Estrutura frontend por features e acessibilidade bloqueante, sem features vazias. |
| ADR-0009 | Plano de commit humano, sem operação Git mutável pelo agente. |

## Arquivos esperados

Lista indicativa; a File List final deve registrar somente arquivos realmente criados, alterados ou removidos.

```text
backend/
  .dockerignore
  Dockerfile
  mvnw
  mvnw.cmd
  .mvn/wrapper/*
  pom.xml
  src/main/java/.../CreditEngineApplication.java
  src/main/resources/application.yml
  src/main/resources/application-docker.yml
  src/main/resources/db/migration/V1__initialize_schema.sql
  src/test/java/.../*ApplicationTest.java
  src/test/java/.../*FlywayIntegrationTest.java
frontend/
  .dockerignore
  Dockerfile
  package.json
  package-lock.json
  tsconfig*.json
  vite.config.ts
  eslint.config.*
  index.html
  src/app/*
  src/shared/api/*
  src/**/*.test.tsx
compose.yaml
.env.example
README.md
docs/architecture/c4-container.md        # somente se necessário
AI_USAGE.md                              # fatos reais da implementação
```

Arquivos vazios de placeholder na raiz (`pom.xml`, `package.json`, `README.md`) devem ser avaliados explicitamente: consolidar sua finalidade ou removê-los no plano humano, sem manter manifests ambíguos.

## Testes e verificações

| Verificação | Evidência esperada |
|---|---|
| `backend/./mvnw -q spotless:check` | Formatação do backend aprovada. |
| `backend/./mvnw -q verify` | Build, teste de contexto e Flyway/Testcontainers aprovados. |
| `frontend/npm run lint` | Lint sem supressões injustificadas. |
| `frontend/npm run typecheck` | TypeScript strict sem erros. |
| `frontend/npm run test -- --run` | Estados de health disponível/indisponível cobertos. |
| `frontend/npm run build` | Bundle de produção criado. |
| `docker compose config` | Compose válido e variáveis resolvidas. |
| `docker compose up --build` + `docker compose ps` | Três serviços construídos e healthy. |
| Smoke test no navegador ou teste HTTP equivalente | SPA servida e integração com readiness da API comprovada. |
| `check-docs.sh . story` | Gate documental sem erros. |

## Riscos e mitigação

| Risco | Impacto | Mitigação |
|---|---|---|
| Healthcheck passa sem provar integração real | Falso positivo no caminho ponta a ponta | SPA consulta readiness da API; testes cobrem sucesso e falha. |
| Flyway e ORM competem pelo schema | Ambientes divergentes | Flyway exclusivo; geração automática do ORM desabilitada; teste em banco vazio. |
| Frontend usa URL interna do Compose no navegador | SPA não alcança API no host | Separar URL pública de build/runtime e validar pelo navegador. |
| Imagens executam como root ou carregam segredos | Risco de segurança e avaliação negativa | Usuário não-root, `.dockerignore`, inspeção da configuração e ausência de `.env` real. |
| Dependências ou imagens flutuantes quebram reprodutibilidade | Build instável | Lockfiles, wrappers e tags compatíveis fixadas. |
| Fundação antecipa abstrações ou domínio | Escopo inflado e código descartável | Limites explícitos; nenhum módulo/feature/tabela de negócio vazio. |
| Docker/Testcontainers indisponível no ambiente | AC1/AC3 não verificáveis | Tratar como bloqueio de execução e registrar exatamente o que não foi validado. |

## Definition of Ready específica

- [x] Objetivo, valor e recorte exato foram extraídos de E0-S1.
- [x] ACs estão em Given/When/Then e possuem verificação objetiva.
- [x] Stack, containers, migração, configuração e gates estão definidos.
- [x] ADR-0001 a ADR-0009 estão aceitos e referenciados sem alteração.
- [x] Não há contrato financeiro, resultado numérico ou dado de domínio necessário nesta story.
- [x] Estratégia de teste cobre build, configuração, saúde, falha da integração e banco vazio.
- [x] Riscos de segurança, migração, operação e reprodutibilidade foram avaliados.
- [x] E0-S2 e funcionalidades posteriores estão explicitamente fora do escopo.
- [x] A story é implementável como um incremento vertical único.

**Avaliação:** Ready. Não há bloqueio funcional ou arquitetural conhecido para iniciar a implementação.

## Definition of Done específica

- [x] AC1–AC8 atendidos com evidências reais registradas.
- [x] Backend passa `spotless:check` e `verify`, incluindo Flyway em PostgreSQL 16 via Testcontainers.
- [x] Frontend passa lint, typecheck, testes e build com TypeScript strict.
- [x] `docker compose config` passa.
- [x] Em ambiente/volume limpo, Compose constrói e deixa PostgreSQL, API e frontend healthy.
- [x] Smoke path SPA→readiness da API funciona e a falha é apresentada de forma textual e acessível.
- [x] Flyway é a única fonte de schema; H2 e criação automática concorrente estão ausentes.
- [x] Dockerfiles usam runtime não-root e não incluem segredos/artefatos desnecessários.
- [x] `.env.example`, README e documentação afetada correspondem à configuração real.
- [x] Gate documental `story` passa sem erros.
- [x] Story registra tasks, arquivos, comandos, resultados, decisões locais e pendências reais.
- [x] Plano de commits atômicos está preparado para execução humana; nenhum commit foi feito pelo Codex.

## Ambiguidades e bloqueios conhecidos

### Ambiguidades não bloqueantes

1. As versões patch exatas de Spring Boot 3, React, TypeScript, Vite, plugins e imagens ainda serão fixadas nos manifests/lockfiles durante a implementação, respeitando as versões maiores aprovadas.
2. O mecanismo mínimo para servir o bundle Vite no container não foi prescrito; deve ser escolhido sem criar novo container lógico ou framework de aplicação.
3. A migration inicial pode ser estritamente técnica, mas deve comprovar Flyway sobre banco vazio sem antecipar tabelas de domínio.

### Bloqueios

Nenhum bloqueio de planejamento. A indisponibilidade de Docker durante a implementação impediria apenas a comprovação dos ACs integrados e manteria a story fora de `Review`.

## Campos BMAD para registro da implementação

### Dev Agent Record

- **Agent/model:** Codex (GPT-5)
- **Data de início:** 2026-09-23
- **Data de conclusão:** 2026-09-23
- **Branch observada:** `feature/e0-s1-foundation`
- **Plano de implementação:** contexto e ADRs → backend/Flyway → frontend → containers/Compose → documentação → gates → revisão BMAD.
- **Debug log:** Maven exigiu Java 21 portátil; Docker Desktop foi iniciado para Testcontainers; a porta 5432 estava ocupada por container externo e o smoke usou `POSTGRES_PORT=5433`; o healthcheck nginx foi corrigido para IPv4; o gate documental passou a excluir diretórios gerados.
- **Completion Notes:** caminho ponta a ponta executável, sem domínio, com readiness dependente do banco, SPA acessível, Flyway técnico e três containers healthy.
- **Decisões locais e justificativas:** nginx-unprivileged para runtime estático; tabela `application_metadata` estritamente técnica para provar Flyway; URL pública da API incorporada no build Vite; versões patch fixas e lockfile auditado.
- **Desvios da story:** nenhum desvio funcional. O diff de revisão foi produzido sem staging porque Git mutável é proibido. A porta PostgreSQL externa foi sobrescrita somente na validação local devido a conflito alheio ao projeto.
- **Riscos/pendências remanescentes:** polling contínuo da readiness e E2E automatizado do navegador não pertencem a E0-S1; os três documentos de escala/EDA/observabilidade seguem previstos apenas para release.

### Evidências dos critérios de aceite

| AC | Status | Teste/comando/evidência |
|---|---|---|
| AC1 | Passed | `docker compose up --build -d` com `POSTGRES_PORT=5433`; `docker compose ps` mostrou os três serviços healthy. |
| AC2 | Passed | SPA real exibiu “API disponível”; testes cobrem UP, DOWN, HTTP 503 e erro de rede. |
| AC3 | Passed | Testcontainers PostgreSQL 16.6 e consulta real a `flyway_schema_history` retornou `1:true`. |
| AC4 | Passed | `docker compose config`, `.env.example` e inspeção de manifests confirmaram configuração externa sem segredo real. |
| AC5 | Passed | builds multi-stage; `id` retornou `creditengine` (uid 100) e `nginx` (uid 101). |
| AC6 | Passed | `spotless:check` e `verify`: 3 testes, 0 falhas/erros/skips. |
| AC7 | Passed | lint, typecheck, 4 testes e build passaram; cobertura 100% statements/lines/functions e 81,25% branches. |
| AC8 | Passed | README seguido para config, down/up, status, URLs e smoke; gate documental com 0 erros. |

### File List

| Operação | Arquivo | Motivo |
|---|---|---|
| Criado | `backend/` | Aplicação Spring Boot, wrapper, Flyway, testes e imagem não-root. |
| Criado | `frontend/` | SPA React strict, testes, lockfile, nginx e imagem não-root. |
| Alterado | `.env.example`, `compose.yaml` | Variáveis e orquestração dos três serviços. |
| Criado | `README.md` | Instruções operacionais verificadas. |
| Alterado | `AI_USAGE.md`, `docs/architecture/c4-container.md` | Uso material de IA e estado arquitetural implementado. |
| Criado/alterado | `docs/database/ddl.sql`, `docs/database/er.md` | Estado técnico efetivo da migration separado do modelo futuro. |
| Alterado | `.gitignore` | Exclusão de metadata incremental TypeScript gerada. |
| Alterado | `.agents/skills/srm-documentacao/scripts/check-docs.sh` | Exclusão de diretórios gerados na validação de links. |
| Criado | `_bmad-output/implementation-artifacts/epic-0-context.md` | Contexto compilado do épico para o workflow. |
| Criado | `_bmad-output/implementation-artifacts/spec-e0-s1-subir-esqueleto-ponta-a-ponta.md` | Especificação e registro de revisão bmad-build. |
| Alterado | Esta story | Tasks, evidências, gates, decisões e handoff. |
| Removido antes de versionar | `package.json`, `pom.xml` da raiz | Placeholders vazios e ambíguos; manifests reais vivem nas aplicações. |

### Testes e gates executados

| Data | Comando | Resultado | Observações |
|---|---|---|---|
| 2026-09-23 | `backend/.\\mvnw.cmd -q spotless:check` | Passed | Java 21. |
| 2026-09-23 | `backend/.\\mvnw.cmd -q verify` | Passed | 3 testes; Flyway/Testcontainers PostgreSQL 16.6 e readiness com DB. |
| 2026-09-23 | `frontend/npm run lint` | Passed | Zero warnings. |
| 2026-09-23 | `frontend/npm run typecheck` | Passed | TypeScript strict. |
| 2026-09-23 | `frontend/npm run test -- --run` | Passed | 4/4; cobertura 100/81,25/100/100. |
| 2026-09-23 | `frontend/npm run build` | Passed | Bundle Vite de produção. |
| 2026-09-23 | `docker compose config` | Passed | Variáveis resolvidas. |
| 2026-09-23 | `docker compose down` + `up --build -d` + `ps` | Passed | PostgreSQL em 5433 por conflito local; três healthy. |
| 2026-09-23 | `pg_isready`, Flyway SQL, API/CORS e SPA HTTP/browser | Passed | Banco accepting; `1:true`; API 200 UP; SPA 200 e estado disponível. |
| 2026-09-23 | `check-docs.sh . story` | Passed | 0 erros; 3 avisos esperados de release. |
| 2026-09-23 | `npm audit` | Passed | 0 vulnerabilidades. |

### Change Log

| Data | Alteração | Autor/agente |
|---|---|---|
| 2026-09-23 | Story preparada a partir dos artefatos BMAD e ADRs aceitos; nenhuma implementação realizada. | Codex |
| 2026-09-23 | E0-S1 implementada, validada e revisada; readiness passou a incluir PostgreSQL e CORS ganhou teste explícito. | Codex |

### Handoff / próximo passo exato

Revisão humana e execução manual do plano de commits atômicos. Nenhuma operação Git mutável foi executada pelo agente.

# Epic 0 Context: Fundação executável e guardrails

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Estabelecer uma fundação local, reproduzível e verificável para o SRM Credit Engine, permitindo que um avaliador suba SPA, API e PostgreSQL por Docker Compose antes de qualquer funcionalidade financeira. O épico reduz riscos de integração, fixa os gates básicos e prepara limites arquiteturais automatizáveis sem antecipar módulos, contratos ou infraestrutura de stories posteriores.

## Stories

- Story E0-S1: Subir o esqueleto ponta a ponta
- Story E0-S2: Impor limites arquiteturais e CI

## Requirements & Constraints

- Um checkout limpo deve construir e iniciar PostgreSQL 16, API Java 21/Spring Boot 3 e SPA React/TypeScript/Vite por Docker Compose.
- Os três serviços devem possuir healthchecks reais, e a SPA deve consultar a readiness da API por URL pública configurável.
- Flyway é a única autoridade do schema; a inicialização deve ocorrer automaticamente em PostgreSQL real e ser comprovada com Testcontainers, sem H2.
- Backend e frontend devem passar seus gates locais de formatação, testes, tipagem e build.
- Configuração deve vir de variáveis de ambiente com defaults locais seguros; `.env` real e segredos não podem integrar imagens ou arquivos versionáveis.
- Dockerfiles devem ser reproduzíveis, multi-stage e executar com usuários não-root.
- A fundação não inclui autenticação, domínio financeiro, schema de negócio, Kafka, Redis, Supabase, microserviços, observabilidade completa nem infraestrutura de alta escala.
- A interface mínima deve usar HTML semântico, comunicar loading/disponibilidade/falha por texto, manter foco visível e não depender apenas de cor.

## Technical Decisions

- O repositório é um monorepo com aplicações independentes em `backend/` e `frontend/`, PostgreSQL local e orquestração oficial por Docker Compose.
- O backend evoluirá como monólito modular com hexagonal seletiva, mas E0-S1 não cria pacotes ou abstrações de domínio vazios. O health técnico usa Spring Boot Actuator, fora de `/api/v1`.
- PostgreSQL 16 e Flyway formam a base de persistência. Geração automática de schema por ORM fica desabilitada, e a migration inicial é estritamente técnica.
- O frontend nasce apenas com `app/` e infraestrutura transversal em `shared/`; `features/*` surge junto de comportamento de negócio real.
- E0-S2 adicionará ArchUnit, regras de imports, axe abrangente e CI. Esses guardrails não pertencem a E0-S1.
- Git segue GitHub Flow, Conventional Commits e histórico linear; operações Git mutáveis permanecem sob responsabilidade humana.

## UX & Interaction Patterns

- A página fundacional apresenta um único estado técnico da API: carregando, disponível ou indisponível.
- Estados assíncronos são textuais e semanticamente identificáveis, com região viva apropriada e sem dados fictícios de negócio.
- Navegação e conteúdo usam landmarks, heading principal e foco visível; a verificação WCAG completa dos fluxos de negócio permanece para stories de UI posteriores.

## Cross-Story Dependencies

- E0-S1 fornece aplicações, containers, configuração e gates necessários para E0-S2 adicionar limites arquiteturais e CI.
- Todos os épicos funcionais E1–E6 dependem dessa base executável; nenhum comportamento desses épicos deve ser antecipado aqui.

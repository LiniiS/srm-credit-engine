# SRM Credit Engine

> Plataforma de cessão de crédito multimoedas em construção. A fundação executável já inclui guardrails arquiteturais e CI, ainda sem funcionalidades de negócio.

## Estado atual

O repositório possui um caminho técnico ponta a ponta:

- PostgreSQL 16 inicializado por Flyway;
- API Java 21/Spring Boot 3 com readiness do Actuator;
- SPA React/TypeScript/Vite que consulta a readiness real;
- execução oficial por Docker Compose, com os três serviços usando healthchecks;
- ArchUnit e ESLint impondo os limites arquiteturais aceitos;
- GitHub Actions reproduzindo os gates locais em pull requests.

O primeiro corte de câmbio já oferece catálogo USD/BRL, histórico append-only e consulta da taxa vigente. Cálculos de conversão, precificação, liquidação, extrato e telas de negócio permanecem fora desta story.

## API de câmbio

| Método | Rota | Resultado |
|---|---|---|
| POST | `/api/v1/exchange-rates` | `201 Created`, `Location` e decimal como string |
| GET | `/api/v1/exchange-rates/latest?base=USD&quote=BRL` | versão vigente no relógio UTC do servidor |

## Como rodar

### Pré-requisitos

- Docker Engine com Docker Compose v2;
- portas `5173`, `8080` e `5432` livres, ou sobrescritas por variáveis de ambiente;
- para gates fora dos containers: Java 21 e Node.js 20.19+ com npm.

### Subir o ambiente completo

Nenhum arquivo `.env` é obrigatório. Os defaults locais são fictícios e não devem ser usados em produção.

```bash
docker compose up --build -d
docker compose ps
```

Se a porta PostgreSQL padrão já estiver ocupada:

```bash
POSTGRES_PORT=5433 docker compose up --build -d
```

No PowerShell:

```powershell
$env:POSTGRES_PORT = "5433"
docker compose up --build -d
```

| Serviço | URL/porta padrão | Verificação |
|---|---|---|
| Frontend | <http://localhost:5173> | página apresenta `API disponível` |
| Backend | <http://localhost:8080/actuator/health/readiness> | `{"status":"UP"}` |
| PostgreSQL | `localhost:5432` | healthcheck com `pg_isready` |

Para conferir diretamente:

```bash
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:5173
docker compose exec postgres pg_isready -U srm -d srm_credit_engine
```

### Encerrar

```bash
docker compose down
```

O volume `postgres_data` é preservado. Para removê-lo deliberadamente, a pessoa responsável deve executar `docker compose down -v`.

## Configuração

Copie `.env.example` para `.env` somente quando precisar sobrescrever os defaults:

```bash
cp .env.example .env
```

| Variável | Default local | Uso |
|---|---|---|
| `POSTGRES_DB` | `srm_credit_engine` | nome do banco |
| `POSTGRES_USER` | `srm` | usuário local |
| `POSTGRES_PASSWORD` | `srm_local_password` | senha fictícia local |
| `POSTGRES_PORT` | `5432` | porta publicada do PostgreSQL |
| `BACKEND_PORT` | `8080` | porta publicada da API |
| `FRONTEND_PORT` | `5173` | porta publicada da SPA |
| `VITE_API_URL` | `http://localhost:8080` | URL pública compilada no frontend |
| `FRONTEND_ORIGIN` | `http://localhost:5173` | origem permitida no health da API |

Variáveis `VITE_*` são públicas no bundle e nunca devem conter segredos.

Ao alterar uma porta publicada, mantenha a URL/origem correspondente consistente.
Por exemplo, para publicar a API em `9090`:

```powershell
$env:BACKEND_PORT = "9090"
$env:VITE_API_URL = "http://localhost:9090"
```

Para publicar a SPA em `5180`:

```powershell
$env:FRONTEND_PORT = "5180"
$env:FRONTEND_ORIGIN = "http://localhost:5180"
```

Depois execute novamente `docker compose up --build -d`, pois `VITE_API_URL` é
incorporada ao bundle durante o build do frontend.

## Gates locais

### Backend

```bash
cd backend
./mvnw -q spotless:check
./mvnw -q verify
```

O `verify` executa os testes de contexto/readiness, a integração Flyway com PostgreSQL 16 via Testcontainers e os guardrails ArchUnit. As fixtures negativas ficam somente nos testes e provam que dependências proibidas são detectadas. Docker precisa estar ativo.

No Windows, os comandos equivalentes usam `mvnw.cmd`.

### Frontend

```bash
cd frontend
npm ci
npm run lint
npm run typecheck
npm run test -- --run
npm run build
```

O lint aplica a matriz `app`/`features`/`shared`; testes da própria configuração comprovam imports permitidos e proibidos. Os testes da tela também executam axe nos estados carregando e resolvido. Axe cobre apenas violações automatizáveis e não substitui revisão manual de acessibilidade.

### Integração contínua

O workflow `.github/workflows/ci.yml` executa jobs independentes de backend, frontend e validações do repositório em pull requests e em pushes para `main`. A execução definitiva no GitHub só pode ser comprovada após push e abertura do PR pela autora.

### Documentação e Compose

```bash
docker compose config
bash .agents/skills/srm-documentacao/scripts/check-docs.sh . story
```

## Stack efetiva da fundação

| Camada | Versão/tecnologia | Decisão |
|---|---|---|
| Backend | Java 21, Spring Boot 3.4.2, Maven 3.9.9 | [ADR-0001](docs/adr/0001-adotar-monolito-modular-hexagonal.md) |
| Persistência | PostgreSQL 16.6, Flyway | [ADR-0002](docs/adr/0002-adotar-postgresql-flyway-jpa-jooq.md) |
| Frontend | React 19.0, TypeScript 5.7, Vite 6.4 | [ADR-0008](docs/adr/0008-organizar-frontend-por-features.md) |
| Orquestração | Docker Compose | [C4 Containers](docs/architecture/c4-container.md) |

O backend ainda não cria módulos de negócio: eles nascerão junto de comportamento real. O frontend contém apenas `app/` e infraestrutura HTTP em `shared/`, sem `features/*` vazias.

## Segurança e limitações

- imagens de backend e frontend usam builds multi-stage e runtimes não-root;
- `.dockerignore` exclui `.env`, caches, builds e metadados locais;
- apenas `health` e `info` são expostos pelo Actuator;
- respostas de health não exibem detalhes internos;
- autenticação e autorização estão fora do MVP atual;
- observabilidade completa pertence a stories posteriores; os guardrails desta fundação não substituem revisão arquitetural nem testes das funcionalidades futuras.

Consulte também os [ADRs aceitos](docs/adr/README.md), os [critérios de aceite](docs/acceptance-criteria.md) e o registro de [uso de IA](AI_USAGE.md).

# AGENTS.md

## Objetivo

Este repositório pode ser desenvolvido com Codex, Claude, Grok ou outro agente. As regras deste arquivo são independentes da ferramenta e devem ser obedecidas por qualquer agente que trabalhe no projeto.

## Ordem de precedência

Em caso de conflito, siga esta ordem:

1. `docs/input/desafio-tecnico.md` — requisitos originais do desafio.
2. ADRs com status `Aceito` em `docs/adr/` — decisões aprovadas pelo responsável.
3. Story BMAD ativa — escopo e critérios de aceite do incremento atual.
4. Arquitetura e PRD do BMAD.
5. Este `AGENTS.md`.
6. Skills e referências em `.agents/skills/`.
7. Preferências do agente ou convenções implícitas.

Se duas fontes de maior prioridade forem incompatíveis, interrompa o trabalho, descreva o conflito e solicite decisão. Não escolha silenciosamente.

## Leitura obrigatória antes de trabalhar

Antes de planejar, implementar ou revisar:

1. Leia este arquivo.
2. Leia `docs/input/desafio-tecnico.md`.
3. Identifique e leia os artefatos BMAD relevantes em `_bmad-output/`.
4. Leia a story ativa por completo, incluindo critérios de aceite, tasks e Dev Notes.
5. Leia os ADRs relacionados.
6. Leia o código, configuração e testes que serão afetados.
7. Inspecione `git status` e `git diff` sem modificar o Git.

Não reinicie o planejamento do projeto se PRD, arquitetura, épicos ou stories já existirem.

## Skills como normas portáveis

As skills do projeto estão canonicamente em `.agents/skills/`. Agentes sem suporte nativo a skills devem tratá-las como documentação normativa e ler manualmente o `SKILL.md` e as referências aplicáveis.

Caminhos específicos de Codex ou Claude são apenas integrações locais. Eles não substituem nem constituem a fonte versionada das skills do projeto.

Use conforme o escopo:

- `srm-arquitetura`: módulos, arquitetura, schema, contratos públicos e ADRs.
- `srm-java-spring`: backend, Flyway, persistência e testes Java.
- `srm-react`: frontend React e seus testes.
- `srm-documentacao`: README, `AI_USAGE.md`, C4, ER, DDL e demais documentos.
- `srm-git`: inspeção e elaboração do plano de commits; nunca execução.

Se uma skill não puder ser encontrada ou lida, informe a limitação e siga as regras equivalentes registradas nos ADRs, na arquitetura e neste arquivo.

## Stack e limites da solução

- Monorepo com `backend/`, `frontend/`, `docs/` e `infra/` quando aplicável.
- Backend: Java 21 e Spring Boot 3.
- Frontend: React, TypeScript strict e Vite.
- Banco: PostgreSQL 16 executado localmente em container.
- Orquestração oficial: Docker Compose para frontend, backend e PostgreSQL.
- Migrações: Flyway.
- Testes de integração: PostgreSQL real via Testcontainers; não usar H2 para persistência ou concorrência.
- API documentada com OpenAPI/Swagger.
- Strategy para regras de precificação.
- jOOQ para o extrato analítico.
- Arquitetura hexagonal no backend onde ela sustentar isolamento, testes e dependência para dentro, preservando as três camadas exigidas pelo desafio.
- Não utilizar Supabase.
- Não implementar Kafka, Redis, microserviços ou infraestrutura distribuída apenas para demonstrar escala. Alta escala e EDA pertencem à documentação desta entrega, salvo requisito aprovado em nova story/ADR.

## Forma de trabalho

- Trabalhe apenas na story ativa e nos critérios de aceite autorizados.
- Transforme cada critério de aceite em comportamento verificável.
- Prefira teste primeiro para regras financeiras, concorrência, persistência e contratos.
- Implemente o menor incremento completo que atenda à story.
- Não altere Story, Acceptance Criteria ou Dev Notes normativas para adaptar o requisito ao código. Reporte inconsistências.
- Não introduza dependência relevante, mudança de schema, contrato público ou limite arquitetural sem aplicar `srm-arquitetura` e propor ADR quando necessário.
- ADR criado por agente começa como `Proposto`. Somente o responsável humano pode promovê-lo para `Aceito`.
- Preserve mudanças existentes do usuário e de outros agentes.
- Não declare comando, teste, build ou validação como aprovado sem executá-lo.
- Se um gate não puder ser executado, registre exatamente o motivo e o que permaneceu sem verificação.

## Regras de domínio e qualidade

- Dinheiro e taxas usam `BigDecimal`/tipos decimais; nunca `double`, `float`, `Number` ou `parseFloat` para valores monetários.
- A fórmula financeira existe somente no backend.
- Arredondamento, prazo, taxa base e direção cambial seguem ADRs aceitos.
- Chamadas externas não ocorrem dentro de transações de banco abertas.
- Liquidação deve ser ACID, idempotente e segura sob concorrência, inclusive quando chaves idempotentes diferentes disputam o mesmo recebível.
- Entidades de persistência não vazam para a API.
- Erros HTTP seguem `application/problem+json` e não expõem stack trace, SQL ou detalhes internos.
- Segredos e arquivos `.env` reais não entram no repositório.
- Não desabilite testes, hooks, lint ou validações para fazer o gate passar.

## Gates mínimos

Execute os gates aplicáveis antes de concluir uma task.

Backend:

```bash
cd backend
./mvnw -q spotless:check
./mvnw -q verify
```

Frontend:

```bash
cd frontend
npm run lint
npm run typecheck
npm run test -- --run
npm run build
```

Ambiente integrado:

```bash
docker compose config
docker compose up --build
docker compose ps
```

Documentação:

```bash
bash .agents/skills/srm-documentacao/scripts/check-docs.sh . story
```

Use `release` no lugar de `story` para o gate final da entrega.

## Git: responsabilidade exclusivamente humana

Agentes podem executar somente comandos Git de leitura, como:

- `git status`
- `git diff`
- `git diff --cached`
- `git log`
- `git show`
- `git branch --show-current`

Agentes não podem executar operações Git mutáveis, incluindo:

- `git add`, `commit`, `switch`, `checkout` ou `restore`;
- criação ou exclusão de branches;
- `merge`, `rebase`, `reset`, `stash` ou `clean`;
- `tag`, `revert` ou `cherry-pick`;
- `push`, force-push ou alterações em remotos.

Ao concluir, o agente deve apenas propor commits atômicos em Conventional Commits, informando arquivos, ordem, mensagem e comandos para execução manual pelo usuário.

## Continuidade entre agentes

Antes de assumir trabalho iniciado por outro agente:

1. Leia a story ativa e seu `Dev Agent Record`/registro equivalente.
2. Inspecione arquivos modificados e diffs ainda não commitados.
3. Confira quais gates já foram realmente executados.
4. Continue do estado existente; não recrie nem substitua trabalho válido.
5. Registre em `AI_USAGE.md` a ferramenta utilizada e contribuições materiais.

Ao interromper ou transferir uma task, deixe registrado na story:

- objetivo atual e critérios de aceite envolvidos;
- arquivos criados ou alterados;
- decisões tomadas e ADRs relacionados;
- testes executados e resultados reais;
- trabalho incompleto, riscos e próximo passo exato;
- plano de commits ainda não executado.

Prefira trocar de agente entre tasks ou stories. Se a troca ocorrer no meio de uma task, o registro acima é obrigatório.

## Saída obrigatória ao concluir trabalho

Informe de forma objetiva:

1. story/task trabalhada;
2. arquivos criados, alterados ou removidos;
3. critérios de aceite atendidos;
4. comandos executados e resultados;
5. decisões, riscos e pendências;
6. documentação atualizada;
7. plano de commits para execução manual.

---
name: srm-documentacao
description: Produz e mantém a documentação do SRM Credit Engine consistente com o código — README (setup, design, decisões, estratégia de branching, design para 1M tx/min), AI_USAGE.md, ADRs (índice), diagramas C4 nível 1 e 2 e ER em Mermaid, DDL, critérios de aceite, proposta EDA e guia de observabilidade. Use sempre que criar ou alterar qualquer arquivo em /docs, README.md ou AI_USAGE.md; após mudanças de schema, endpoint, stack, portas, variáveis de ambiente ou docker-compose; ao fechar uma story ou épico do BMAD; ao preparar release/tag; ou quando o agente Tech Writer/PM do BMAD for gerar documentação de projeto.
---

# SRM Credit Engine — Documentação

O desafio diz que o README é a "cara" do projeto e avalia fundamentação teórica e domínio do negócio. Documentação aqui é **evidência de engenharia**: cada afirmação deve ser verdadeira no código atual e cada decisão deve apontar para seu ADR.

## Princípios

- **Verdade verificável:** todo comando, porta, versão, variável e endpoint citado é conferido no código/config antes de escrever. Documento desatualizado é pior que ausente.
- **Fonte única:** não copie conteúdo que já vive em outro lugar — linke. ADR guarda o porquê; `architecture.md` (BMAD) guarda o estado; README resume e aponta; Swagger é a referência da API; migrações Flyway são a fonte do DDL.
- **Leitor primeiro:** o avaliador precisa, em 5 minutos, rodar o projeto e entender as decisões. Ordem: o que é → como rodar → como está desenhado → por quê → como evoluir.
- **Concisão:** frases curtas, tabelas para comparação, diagramas para estrutura. Sem marketing, sem adjetivos vazios.
- **Honestidade:** limitações conhecidas e o que ficou fora do escopo são explicitados (ex.: sem autenticação).

## Passo 0 — Carregar contexto

1. Leia o que mudou: `git diff --stat main...HEAD` (ou a File List da story BMAD).
2. Leia os artefatos BMAD (PRD, architecture, stories concluídas) e `docs/adr/`.
3. Leia a configuração real: `docker-compose.yml`, `pom.xml`/`build.gradle`, `package.json`, `application*.yml`, `.env.example`, workflows de CI, migrações.
4. Leia `references/estrutura-documental.md` (mapa de documentos e templates).

## Workflow

1. **Mapear impacto** — use a tabela "Gatilhos → documentos" abaixo para listar o que precisa mudar.
2. **Atualizar** — edite somente as seções afetadas, preservando o restante.
3. **Diagramas** — Mermaid (renderiza no GitHub): `C4Context`/`C4Container` e `erDiagram`. Conferir nomes de tabelas/colunas com as migrações e nomes de containers com o compose.
4. **Verificar** — rode `scripts/check-docs.sh` a partir da raiz do repositório (arquivos obrigatórios, links relativos quebrados, índice de ADR, TODOs esquecidos). Execute mentalmente (ou de fato, se possível) as instruções de "Como rodar" do zero.
5. **Reportar** — liste o que foi alterado e o que foi verificado.

## Gatilhos → documentos

| Mudança | Atualizar |
|---|---|
| Nova migração / schema | `docs/database/er.md`, `docs/database/ddl.sql`, README (seção modelo de dados, se resumo mudar) |
| Novo endpoint / mudança de contrato | README (tabela de endpoints), exemplos `curl`, `docs/api/` se houver export |
| Nova decisão arquitetural | `docs/adr/README.md` (índice), README (seção decisões), C4 se afetar containers |
| Mudança em compose/portas/env | README "Como rodar", `.env.example`, C4 container |
| Nova dependência relevante | README "Stack e justificativas" + ADR |
| Uso relevante de IA numa story | `AI_USAGE.md` (a partir das Completion Notes da story) |
| Métrica/log/dashboard novo | `docs/observability.md` |
| Workflow Git/CI/hook | README "Fluxo de trabalho Git", `docs/` de CI |
| Release | `CHANGELOG.md` (se adotado), README badge/versão |

## Documentos obrigatórios (nível sênior + itens de especialista)

- `README.md` — template em `references/estrutura-documental.md`.
- `AI_USAGE.md` — prompts estratégicos, alucinações/código inseguro e correções, análise crítica. Exigido pelo desafio.
- `docs/adr/` — índice + ADRs (conteúdo vem de `srm-arquitetura`).
- `docs/architecture/c4-context.md` e `c4-container.md`.
- `docs/database/er.md` e `docs/database/ddl.sql`.
- `docs/acceptance-criteria.md` — usabilidade, segurança, desempenho, escalabilidade (requisito não funcional 2).
- `docs/scale/high-scale-design.md` — 1M transações/minuto (cache, sharding, consistência eventual).
- `docs/eda/event-model.md` — proposta de arquitetura orientada a eventos.
- `docs/observability.md` — logs, métricas, dashboards, como acessar.

## Regras para o AI_USAGE.md

É avaliado para saber se a IA "potencializou a engenharia ou mascarou falta de conhecimento". Portanto:
- Registre prompts **estratégicos** (scaffolding, massa de dados, refatoração de query, geração de testes), não cada conversa.
- Para cada alucinação/código inseguro: o que a IA gerou, como foi detectado (teste, revisão, ArchUnit, skill de segurança), a correção aplicada e o commit.
- Inclua o próprio uso de BMAD e das skills como parte do processo (é um diferencial legítimo).
- Análise crítica equilibrada: onde economizou tempo, onde atrapalhou, o que foi feito à mão e por quê.
- Nunca invente episódios. Se não houver registro, peça ao usuário ou deixe marcado para preenchimento.

## Estilo

- Português do Brasil em toda a documentação (código e commits seguem as convenções da skill `srm-git`).
- Termos técnicos consagrados em inglês podem permanecer (Strategy, Optimistic Locking, Circuit Breaker).
- Títulos com `#` hierárquicos; tabelas para comparações; blocos de código com linguagem declarada.
- Datas em ISO-8601; valores monetários de exemplo com moeda explícita.

## Integração com BMAD

- **Architect:** ao finalizar `architecture.md`, gere/atualize C4, ER e índice de ADR a partir dele.
- **Dev (fim da story):** ler a File List e as Completion Notes → aplicar a tabela de gatilhos; alimentar `AI_USAGE.md`.
- **Tech Writer / document-project:** use o template de README e os gatilhos; não duplique o PRD — linke.
- **Fim de épico / release:** revisão completa com `scripts/check-docs.sh` antes da tag (coordenar com `srm-git`).

## Saída esperada

```markdown
## Documentação atualizada
| Arquivo | Alteração |
|---|---|

## Verificações
- check-docs.sh: ✅ / ❌ (<itens>)
- Comandos de "Como rodar" conferidos com: <compose, package.json, pom>
- Diagramas conferidos com: <migrações / compose>

## Pendências para o usuário
- <informações que só o autor tem, ex.: episódios de IA>
```

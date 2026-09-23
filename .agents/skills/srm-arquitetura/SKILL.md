---
name: srm-arquitetura
description: Planeja, revisa e documenta decisões estruturais do SRM Credit Engine — monorepo, monólito modular, arquitetura hexagonal, contratos públicos, modelo relacional, precisão financeira, concorrência, resiliência e observabilidade. Use ao criar ou alterar limites de módulos, dependências relevantes, schema, contratos públicos, padrões transversais ou decisões de alto custo de reversão; também nos workflows Architect do BMAD e em stories que atravessem módulos. Não use para implementação interna já coberta por decisão aceita.
---

# SRM Credit Engine — Arquitetura

Esta skill garante que toda mudança estrutural seja **intencional, justificada e rastreável**. O avaliador do desafio lê o repositório procurando exatamente isso: por que cada peça existe e por que não outra. Um bom ADR vale mais que uma abstração a mais.

## Quando esta skill manda e quando não manda

- **Manda:** criação/alteração de módulo ou camada, dependência estrutural, schema, contrato público, padrão transversal, precisão financeira, concorrência ou escala.
- **Não manda:** implementação interna de uma classe dentro de limites já decididos → use `srm-java-spring` ou `srm-react`.
- Se durante uma implementação surgir a necessidade de quebrar um limite, **pare**, invoque esta skill, proponha a decisão e só então continue após aprovação quando necessária.

## Passo 0 — Carregar contexto (obrigatório)

1. Localize os artefatos BMAD. Procure, nesta ordem: `_bmad-output/planning-artifacts/`, `docs/`, `.bmad-core/core-config.yaml` (v4 — leia `architectureFile`, `architectureSharded`, `prdFile`). Leia PRD, `architecture.md` (ou shards) e epics/stories relevantes.
2. Leia `docs/adr/README.md` e os ADRs aceitos relacionados à tarefa. Não carregue ADRs irrelevantes. ADRs aceitos são lei até serem substituídos.
3. Leia `references/baseline-arquitetural.md`: é a arquitetura de referência calibrada para o escopo sênior, com PostgreSQL local orquestrado por Docker Compose.
4. Se houver conflito entre story, `architecture.md` e ADR aceito: **HALT** e reporte o conflito com as opções. Não escolha silenciosamente.

## Princípios de decisão

Aplique nesta ordem de prioridade quando houver tensão:

1. **Correção financeira** — precisão decimal, atomicidade, auditabilidade. Não negociável.
2. **Simplicidade (KISS/YAGNI)** — monólito modular antes de microserviços; a solução mais simples que atende aos critérios de aceite *atuais*. Tudo que for "para 1M tx/min" vai para documentação de design (`docs/scale/`), não para o código.
3. **Baixo acoplamento (SOLID)** — dependências apontam para dentro; regras de risco plugáveis via Strategy (OCP); portas de caso de uso/infra definidas na aplicação e contratos intrinsecamente de negócio no domínio (DIP).
4. **Observabilidade e operabilidade** — se não dá para medir ou depurar em produção, não está pronto.
5. **Conveniência do desenvolvedor** — por último.

Para cada padrão proposto, responda por escrito: *qual problema concreto deste projeto ele resolve?* Se a resposta for "pode ser útil no futuro", não adote (YAGNI) — registre como "considerado e rejeitado" no ADR.

## Workflow

### 1. Enquadrar a decisão
Escreva em 3–5 linhas: contexto, força em jogo (ex.: precisão vs. performance), requisito do desafio ou AC da story que motiva, e o que acontece se nada for feito.

### 2. Levantar opções
No mínimo 2 opções reais (inclua "não fazer nada" quando fizer sentido). Para cada uma: prós, contras, custo de reversão, impacto em testes e em operação.

### 3. Decidir e delimitar
- Escolha e justifique ligando aos princípios acima.
- Declare **limites do módulo**: o que o módulo expõe (API pública/ports), o que é interno, de quem pode depender e de quem **não** pode.
- Declare como a regra será **verificada automaticamente** (ArchUnit, teste de contrato, lint, migração Flyway, teste de concorrência). Uma decisão sem verificação vira sugestão.

### 4. Registrar quando a decisão for material
- Crie ADR somente quando houver alternativas reais, consequência duradoura ou custo relevante de reversão. Alteração rotineira de endpoint ou coluna não exige ADR por si só.
- Crie `docs/adr/NNNN-titulo-em-kebab-case.md` com o template em `references/adr-template.md`. Numeração sequencial, 4 dígitos, nunca reutilizada.
- O Codex cria ADR como `Proposto`. Somente a autora/decisora altera para `Aceito`.
- Atualize `docs/adr/README.md` (índice: número, título, status, data).
- Se substituir um ADR, mude o antigo para `Substituído por ADR-NNNN` — nunca apague.
- Se a decisão muda diagramas (C4, ER) ou o README, sinalize para `srm-documentacao`.
- Se o BMAD tiver `architecture.md`, adicione/atualize a seção correspondente com link para o ADR (fonte única: o ADR guarda o *porquê*, o architecture.md guarda o *estado atual*).

### 5. Traduzir em regras executáveis
Produza (ou atualize) as regras que a implementação vai seguir:
- Testes ArchUnit em `backend/src/test/java/.../architecture/` para camadas e dependências entre módulos.
- Regras de import/ESLint (`no-restricted-imports`/boundaries) no frontend quando o limite for de front.
- Critérios de aceite técnicos que a story deve carregar (para o SM/Dev do BMAD).

## Saída esperada (sempre neste formato)

```markdown
## Decisão arquitetural
- ADR: docs/adr/NNNN-....md (status: Proposto|Aceito)
- Resumo: <1–2 frases>

## Limites dos módulos
| Módulo | Expõe | Pode depender de | Não pode depender de |
|---|---|---|---|

## Verificações automáticas
- <teste ArchUnit / regra lint / teste de integração> — arquivo

## Impactos
- Documentação a atualizar: <C4 / ER / README / AI_USAGE>
- Stories BMAD afetadas: <ids>
- Riscos e mitigação: <...>
```

## Catálogo de ADRs essenciais

Verifique se são aplicáveis. Proponha antes de implementar a parte afetada; não crie documentos artificiais apenas para completar a lista:

1. Stack e linguagem (Java 21 + Spring Boot 3, React + TS) — justificativa para ambiente financeiro.
2. Monólito modular vs. microserviços.
3. PostgreSQL local em Docker Compose e estratégia de migrações Flyway.
4. Representação monetária: `BigDecimal`/`NUMERIC`, escalas, `RoundingMode`, cálculo de potência com prazo fracionário.
5. Convenção do prazo (dias corridos/30, dias úteis/21, meses inteiros) e da taxa base (origem, vigência).
6. Convenção de câmbio (par base/cotação, direção da conversão, snapshot da taxa na liquidação, taxa "velha").
7. Concorrência sobre o recebível: `@Version`, transição de estado, unicidade de `settlement_item.receivable_id` e idempotência da requisição.
8. Estratégia de relatórios: JPA na escrita, jOOQ/SQL nativo na leitura (CQRS leve, sem event sourcing).
9. Formato de erros (RFC 9457 `ProblemDetail`).
10. Observabilidade (logs JSON, Micrometer/Prometheus, tracing) e resiliência (Resilience4j no provedor de câmbio).
11. Estado no frontend (TanStack Query para estado de servidor; estado global só se necessário).
12. Fluxo de branching e versionamento (coordene com `srm-git`).

## Anti-padrões a barrar

- Camada "service" que só repassa para repository sem regra (anemia inútil) *ou* controllers com regra de negócio.
- Interfaces com uma única implementação sem motivo de teste/DIP (exceção legítima: ports do domínio e `PricingStrategy`).
- Entidades JPA vazando para controllers/DTOs de resposta.
- `double`/`float` em qualquer valor monetário ou taxa.
- Kafka/Redis/microserviços no código "porque escala" — escala é documento, não implementação, neste escopo.
- Módulo `common`/`utils` virando depósito: só entra ali o que é realmente transversal (Money, erros, clock).
- Decisão arquitetural material tomada no código sem ADR proposto quando se enquadra nos critérios acima.

## Integração com BMAD

- **Agente Architect:** use esta skill ao produzir/atualizar `architecture.md`; cada decisão significativa gera um ADR.
- **Agente SM (create-story):** as seções "Limites dos módulos" e "Verificações automáticas" devem ir para `Dev Notes` da story.
- **Agente Dev:** se a story exigir quebrar um limite, o Dev deve HALT e pedir esta skill — não improvisar.
- **Code review/QA:** verifique se ADRs citados na story existem e se os testes ArchUnit passam.

## Referências

- `references/baseline-arquitetural.md` — arquitetura de referência, módulos, camadas, API e modelo de dados. Leia sempre.
- `references/adr-template.md` — template e exemplo de ADR. Leia ao criar/editar ADR.

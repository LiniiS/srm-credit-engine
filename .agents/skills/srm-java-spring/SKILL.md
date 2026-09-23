---
name: srm-java-spring
description: Implementa e revisa o backend do SRM Credit Engine em Java 21 e Spring Boot 3 com arquitetura hexagonal, Strategy de precificação, BigDecimal, PostgreSQL em Docker, idempotência durável, concorrência, jOOQ, ProblemDetail, Resilience4j, observabilidade e testes. Use ao escrever, alterar, testar ou revisar /backend, Flyway, Dockerfile ou stories BMAD de backend.
---

# SRM Credit Engine — Backend Java/Spring

O objetivo é código que um revisor sênior aprovaria sem comentários: correto em dinheiro, seguro sob concorrência, simples de ler e coberto por testes que provam as regras de negócio.

## Passo 0 — Carregar contexto (obrigatório, antes de planejar)

1. Leia a story BMAD inteira (Story, Acceptance Criteria, Tasks, Dev Notes). Localização típica: `_bmad-output/implementation-artifacts/` ou `docs/stories/`.
2. Leia ADRs citados na story e `docs/adr/README.md`. ADR aceito vence preferência pessoal.
3. Leia os arquivos existentes que vai tocar e seus testes. Siga o estilo já presente.
4. Leia as referências desta skill conforme a tarefa:
   - Precificação, dinheiro, câmbio → `references/dominio-financeiro.md`
   - Persistência, transações, locking, relatórios, migrações → `references/persistencia-e-concorrencia.md`
   - Controllers, validação, erros, observabilidade, resiliência → `references/api-e-operacao.md`
   - Qualquer implementação → `references/testes.md` e `references/clean-code-java.md`
5. Se algo exigir mudar limites de módulo, dependência relevante ou schema não previsto: interrompa a implementação e aplique `srm-arquitetura`; registre a decisão antes de continuar.

## Workflow (ciclo por task da story)

1. **Entender o AC** — reescreva mentalmente cada AC como um cenário Given/When/Then testável.
2. **Teste primeiro (red)** — escreva o teste que falha: unitário para regra de domínio, integração (Testcontainers) para persistência/concorrência, `@WebMvcTest` ou MockMvc para contrato HTTP.
3. **Implementar o mínimo (green)** — na camada correta. Nada de código especulativo.
4. **Refatorar** — nomes, funções pequenas, remoção de duplicação real (não acidental).
5. **Verificar** — rode o gate completo (abaixo). Só marque a task `[x]` com tudo verde.
6. **Registrar** — atualize a story (seções permitidas) e peça a `srm-git` somente o plano de commits; o usuário executa todos os comandos mutáveis de Git.

Se o mesmo teste falhar 3 vezes seguidas com abordagens diferentes: **HALT**, descreva hipóteses e peça orientação. Não desabilite testes nem use `@Disabled` para "passar".

## Regras inegociáveis

- **Dinheiro e taxas:** `BigDecimal` (ou VOs `Money`/`Rate`), nunca `double`/`float`. Construa com `new BigDecimal("1.5")` ou `BigDecimal.valueOf(long, scale)` — nunca `new BigDecimal(1.5)`. Compare com `compareTo`, não `equals`. Arredonde uma vez, no final, com `RoundingMode` explícito.
- **Hexagonal:** `adapter.in.web` chama portas de entrada; `application.usecase` orquestra; `domain` contém regras; `adapter.out.*` implementa portas de saída. Domínio e aplicação não dependem de adapters.
- **Transação:** fronteira na aplicação ou em decorator transacional, nunca no controller. Nada de chamada HTTP externa dentro da transação — obtenha a taxa antes e persista seu snapshot.
- **Concorrência:** `@Version` no recebível, `Idempotency-Key` com hash do payload e `UNIQUE(settlement_item.receivable_id)`; duas chaves diferentes não podem liquidar o mesmo recebível.
- **Banco:** PostgreSQL 16 é a fonte da verdade; desenvolvimento sobe com `docker compose`, migrações são Flyway e testes de integração usam PostgreSQL via Testcontainers, nunca H2.
- **Entrada:** Bean Validation nos DTOs (`@NotNull`, `@Positive`, `@Digits(integer=17, fraction=2)`, `@FutureOrPresent`), validação de regra no domínio. Rejeite campos desconhecidos (`FAIL_ON_UNKNOWN_PROPERTIES`).
- **Erros:** um único `@RestControllerAdvice` produzindo `ProblemDetail`; exceções de domínio tipadas; nunca vazar stack trace, SQL ou nome de classe na resposta.
- **Segredos:** nada hardcoded; tudo via variáveis de ambiente/`application-*.yml` sem valores reais.
- **Tempo:** injete `java.time.Clock`; nunca `LocalDate.now()` direto em regra (testabilidade).
- **Logs:** SLF4J com placeholders, sem concatenação; sem dados pessoais em claro; `info` para eventos de negócio, `debug` para detalhe, `error` só com contexto e exceção.

## Princípios aplicados (checar em cada classe)

- **SRP:** uma razão para mudar. `PricingService` orquestra; `PricingStrategy` calcula spread; `FinancialMath` faz potência.
- **OCP:** novo tipo de recebível = nova classe `PricingStrategy` + registro no banco; zero `if/switch` no calculador.
- **LSP:** toda strategy respeita o contrato (entrada válida → spread ≥ 0, sem exceção surpresa).
- **ISP:** ports pequenos (`ExchangeRateQuery` só lê; `ExchangeRateCommand` só grava).
- **DIP:** aplicação define portas; adapters de persistência e provedores implementam. Portas estritamente de domínio podem morar no domínio quando seu contrato for parte da linguagem de negócio.
- **KISS/YAGNI:** sem generics, hierarquias ou "frameworks internos" que o AC não pede. Interface só quando há ≥2 implementações ou fronteira de port.
- **DRY:** extraia apenas conhecimento duplicado (a mesma regra), não código parecido por coincidência.
Detalhes e exemplos: `references/clean-code-java.md`.

## Gate de verificação (rodar antes de concluir qualquer task)

```bash
cd backend
./mvnw -q spotless:check        # formatação (ou ./gradlew spotlessCheck)
./mvnw -q verify                # compila, unit + integration (Testcontainers), ArchUnit, JaCoCo
```
Critérios: build verde; cobertura de linhas do pacote `pricing.domain` ≥ 90% e do projeto ≥ 80% (ajuste conforme ADR); nenhum warning novo do compilador; nenhum teste ignorado novo. Se Docker não estiver disponível para Testcontainers, informe explicitamente — não finja que passou.

## Checklist de revisão (modo code-review)

Ao revisar, produza achados classificados como **Bloqueante / Importante / Sugestão**, com arquivo:linha e correção proposta:
- [ ] Algum `double`, `float`, `Math.pow` ou `new BigDecimal(double)` em valores financeiros?
- [ ] Regra de negócio fora do domínio? Entidade JPA exposta na API?
- [ ] `@Transactional` no lugar certo? Chamada externa dentro da transação?
- [ ] Liquidação protegida por `@Version` + idempotência + constraint?
- [ ] Todos os erros mapeados para status semântico via handler global?
- [ ] Validação de entrada completa (limites, escala, datas, enums)?
- [ ] Queries do relatório parametrizadas (sem concatenação de SQL), paginadas e indexadas?
- [ ] Testes cobrem caminho feliz, bordas (prazo 0/1 dia, valores grandes, arredondamento) e falhas?
- [ ] Logs sem PII, com contexto útil? Métrica de negócio emitida?
- [ ] Nome de classes/métodos expressa intenção do domínio (linguagem ubíqua: cedente, deságio, liquidação)?
- [ ] CORS restrito, segredos fora do código, dependências verificadas e container executando como usuário não-root?

## Integração com BMAD (dev-story)

- Edite na story **apenas**: checkboxes de Tasks/Subtasks, `Dev Agent Record` (modelo usado, debug log, completion notes, File List), `Change Log` e `Status`. Nunca altere Story, Acceptance Criteria ou Dev Notes — se estiverem errados, HALT e reporte.
- `File List` deve conter todo arquivo criado/alterado/removido (caminho relativo).
- Em `Completion Notes`, registre decisões locais, desvios e dívidas — isso alimenta o `AI_USAGE.md` (via `srm-documentacao`).
- Status final `Review` somente após o gate verde. Definição de pronto: ACs cobertos por testes, execução local via Compose verificada, story atualizada e plano de commits preparado via `srm-git`; nenhum commit é criado pelo Codex.

## Saída esperada ao final

```markdown
## Implementação
- Story: <id> — Tasks concluídas: <lista>
- Arquivos: <lista resumida>

## Verificações
- spotless: ✅ | verify: ✅ (<n> testes) | cobertura pricing.domain: <x>%
- Testes novos: <lista com o que cada um prova>

## Decisões e dívidas
- <decisões locais; ADR necessário? sim/não>

## Próximo passo
- Commits sugeridos (via srm-git): <mensagens>
```

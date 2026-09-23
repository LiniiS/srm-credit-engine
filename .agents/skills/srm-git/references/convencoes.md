# Convenções de Git e Pull Request

## Estratégia de branching (para a seção do README)

**Escolha padrão: GitHub Flow + branches curtas + rebase (próximo de Trunk-Based).**

Justificativa para este projeto:
- Um desenvolvedor, entregas em 3–4 dias, deploy contínuo possível via CI: não há releases paralelas que justifiquem `develop`/`release/*` do Git Flow.
- `main` sempre verde e implantável; cada story entra por PR revisado + CI.
- Histórico linear facilita `bisect`, `revert` e leitura pelo avaliador.
- Hotfix: branch `hotfix/*` a partir da tag em produção, cherry-pick para `main` (demonstra o cenário de crise).

Alternativas consideradas: Git Flow (overhead de branches longas sem ganho aqui); Trunk-Based puro com commits diretos na `main` (conflita com a exigência de PRs).

## Regras de atomicidade

- Um commit resolve **uma** intenção e deixa o build verde.
- Testes vão junto do código que provam. Commit só de teste é válido quando **adiciona** cobertura a código já existente (ex.: teste de concorrência) — tipo `test`.
- Refatoração nunca junto de mudança de comportamento: `refactor` primeiro, `feat`/`fix` depois.
- Formatação em massa em commit `style` isolado (nunca misturada).
- Migração de banco em commit próprio `feat(db)`/`fix(db)`.
- Atualização de dependência em commit `build(deps)` próprio.

## Mapeamento story → commits (exemplo)

Story 2.3 "Precificar recebível por tipo":
1. `feat(db): add receivable_type table with spread and strategy key`
2. `feat(pricing): add money and rate value objects`
3. `feat(pricing): add pricing strategies for duplicata and cheque`
4. `feat(pricing): compute present value with fractional term using big-math`
5. `feat(api): expose pricing simulation endpoint`
6. `docs(adr): record rounding and term conventions`

## Template de Pull Request (`.github/pull_request_template.md`)

```markdown
## Contexto
Story: <id e título> · Épico: <id>
<Por que esta mudança existe, em 2–3 linhas>

## O que foi feito
- <mudança 1>
- <mudança 2>

## Decisões
- <decisão e motivo> (ADR-NNNN, se houver)

## Critérios de aceite
- [x] AC1 — <como foi verificado: teste X>
- [x] AC2 — ...

## Como testar
    ./mvnw verify
    curl -X POST localhost:8080/api/v1/pricing/simulations -H 'Content-Type: application/json' -d '{...}'

## Checklist
- [ ] Commits atômicos em Conventional Commits, histórico rebaseado
- [ ] Gate verde (lint, testes, build)
- [ ] Sem segredos/arquivos gerados
- [ ] Documentação atualizada (README/docs/ADR/AI_USAGE) ou N/A
- [ ] Revisão de segurança aplicada quando há endpoint/config novo

## Riscos e rollback
<impacto, como reverter (git revert <sha>), migração reversível?>
```

## Comandos úteis

```bash
git add -p                                  # stage por trechos
git commit --fixup=<sha>                    # correção de commit anterior
git rebase -i --autosquash origin/main      # organizar
git log --oneline --graph --decorate -20    # conferir linearidade
git range-diff origin/main@{1} origin/main HEAD   # comparar antes/depois do rebase
```

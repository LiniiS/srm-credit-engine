# ADR-0009: Adotar GitHub Flow com histórico linear

- **Status:** Proposto
- **Data:** 2026-09-23
- **Decisores:** responsável pelo projeto
- **Relacionado a:** expectativas de versionamento sênior/especialista

## Contexto

O desafio avalia commits atômicos, PRs, histórico limpo, SemVer e gestão de crise. É um projeto curto, sem linhas paralelas de release.

## Opções consideradas

1. **GitHub Flow, branches curtas, rebase e rebase-merge** — baixo overhead e histórico linear.
2. **Git Flow** — suporta releases paralelas; complexidade sem benefício neste prazo.
3. **Trunk-based com commit direto em main** — simples; conflita com exigência de PR.

## Decisão

Propor opção 1. Cada story usa branch curta, commits Conventional Commits atômicos e PR com gates. Tags SemVer e simulação de `revert`/`cherry-pick` são responsabilidade humana.

## Limites e regras resultantes

- Agentes deste repositório apenas inspecionam Git e propõem comandos; não executam operações mutáveis.
- Nunca burlar hooks; segredos e `.env` reais não entram no histórico.

## Verificação

- CI, commitlint/Husky quando implementados e revisão humana do histórico/PR.

## Consequências

- Positivas: narrativa auditável e `bisect`/revert simples.
- Negativas: exige disciplina manual e rebase cuidadoso.
- Revisitar se: equipe/release train justificar branches de longa duração.

